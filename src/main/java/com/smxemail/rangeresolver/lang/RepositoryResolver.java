package com.smxemail.rangeresolver.lang;

import com.google.common.collect.ImmutableList;
import com.smxemail.rangeresolver.api.AbstractRepository;
import com.smxemail.rangeresolver.api.AbstractResolve;
import com.smxemail.rangeresolver.api.Bound;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RepositoryResolver {

  public static final String BILL_OF_MATERIALS = "bill-of-materials";

  public static final String DEPS = "deps";

  private Consumer<String> infoLogger;
  private Consumer<String> warningLogger;

  private String localRepoDir;

  private boolean updateSnapshots;

  private RepositorySystem repositorySystem;

  private RepositoryResolver(Consumer<String> infoLogger, Consumer<String> warningLogger, String localRepoDir, boolean updateSnapshots) {
    this.infoLogger = infoLogger;
    this.warningLogger = warningLogger;
    this.localRepoDir = localRepoDir;
    this.updateSnapshots = updateSnapshots;
    this.repositorySystem = newRepositorySystem();
  }

  public static RepositoryResolver createRepositoryResolver(
      Consumer<String> infoLogger, Consumer<String> warningLogger, String localRepoDir, boolean updateSnapshots) {
    return new RepositoryResolver(infoLogger, warningLogger, localRepoDir, updateSnapshots);
  }

  private static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    return locator.getService(RepositorySystem.class);
  }

  public RepositorySystemSession newRepositorySystemSession() {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepo = new LocalRepository(localRepoDir);
    session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepo));
    session.setTransferListener(new ConsoleTransferListener(infoLogger));
    return session;
  }

  /**
   * Resolve artifact requests from the resolution model.
   *
   * @param rangeResolver The range resolution model.
   * @return A {@link ResolutionResult}
   * @throws RepositoryException
   */
  public ResolutionResult resolveRanges(RangeResolver rangeResolver, String sourceReference) throws RepositoryException {
    return resolveRanges(ImmutableList.of(sourceReference), rangeResolver, sourceReference);
  }

  /**
   * Resolve artifact requests from the resolution model.
   *
   * @param previouslyImportedGavs A list of previously imported bill-of-material GAVs, including the current projects bill-of-materials reference.
   * @param rangeResolver The range resolution model.
   * @return A list of resolved artifacts
   * @throws RepositoryException
   */
  public ResolutionResult resolveRanges(List<String> previouslyImportedGavs, RangeResolver rangeResolver, String sourceReference) throws RepositoryException {
    RepositorySystemSession session = newRepositorySystemSession();

    List<RemoteRepository> repositories =
        rangeResolver.repositories().stream().map(repo -> buildRepository(repo, updateSnapshots)).collect(Collectors.toList());

    repositorySystem.newResolutionRepositories(session, repositories);

    ResolutionResult resolvedImportedThings =
        rangeResolver
            .imports()
            .stream()
            .map(imported -> processImport(sourceReference, session, repositories, previouslyImportedGavs, Collections.emptyMap(), imported))
            .distinct()
            .collect(ResolutionResult.REDUCING);

    List<MetadataRequest> metadataRequests = rangeResolver.resolves().stream().map(rep -> buildMetaDataRequest(rep)).collect(Collectors.toList());

    infoLogger.accept("Resolving metadata....");
    repositorySystem.resolveMetadata(session, metadataRequests);

    Map<String, Resolved> originallyLockedThings = rangeResolver.locked().stream().collect(Resolved.collectByKey);
    originallyLockedThings.putAll(resolvedImportedThings.locked().stream().collect(Resolved.collectByKey));

    Map<String, Resolved> originallyResolvedThings = rangeResolver.resolved().stream().collect(Resolved.collectByKey);
    originallyResolvedThings.putAll(originallyLockedThings);

    List<Resolved> resolvedThings =
        rangeResolver
            .resolves()
            .stream()
            .filter(resolve -> !isLocked(sourceReference, originallyLockedThings, resolve))
            .map(resolve -> resolveArtifact(sourceReference, session, repositories, originallyResolvedThings, resolve))
            .collect(Collectors.toList());

    Set<Resolved> allResolved = new TreeSet<>(Comparator.comparing(Resolved::key));
    allResolved.addAll(resolvedThings);
    allResolved.addAll(resolvedImportedThings.locked());
    allResolved.addAll(resolvedImportedThings.resolved());
    List<Resolved> sortedUniqueResolved = ImmutableList.copyOf(allResolved);

    warnAboutResolutionConflicts(sortedUniqueResolved);

    return ResolutionResult.of(resolvedImportedThings.aliases(), sortedUniqueResolved, rangeResolver.locked());
  }

  /**
   * Group a list of resolved artifacts by their groupId:artifactId, warn if multiple entries are found.
   * @param resolved A list of {@link Resolved} items.
   */
  private void warnAboutResolutionConflicts(List<Resolved> resolved) {
    Map<String, List<Resolved>> conflicting = resolved.stream().collect(Collectors.groupingBy(Resolved::key));
    for (Map.Entry<String, List<Resolved>> entry : conflicting.entrySet()) {
      if (entry.getValue().size() > 1) {
        String resolutionGavs = entry.getValue().stream().map(Resolved::resolvedFrom).collect(Collectors.joining(", "));
        warningLogger.accept(String.format("Duplicate resolution of %s found in: %s", entry.getKey(), resolutionGavs));
      }
    }
  }

  private ResolutionResult processImport(
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories,
      List<String> previousImports,
      Map<String, Resolved> originalResolvedThings,
      Import i) {

    if (previousImports.contains(i.gav())) {
      throw new IllegalStateException(String.format("Circular imports detected for %s in %s", i.gav(), sourceReference));
    } else {
      try {
        RangeResolver importedRangeResolver = resolveImport(sourceReference, session, repositories, originalResolvedThings, i);
        List<String> updatedImportGavs = ImmutableList.<String>builder().addAll(previousImports).add(i.gav()).build();
        return resolveRanges(updatedImportGavs, importedRangeResolver, i.gav());
      } catch (RepositoryException | IOException e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        warningLogger.accept(sw.toString());
        return ResolutionResult.empty();
      }
    }
  }

  public RangeResolver resolveImport(
      String sourceReference, RepositorySystemSession session, List<RemoteRepository> repositories, Map<String, Resolved> originalResolvedThings, Import i)
      throws ArtifactResolutionException, IOException {
    Resolve imported = Resolve.of("", i.group(), i.artifact(), Bound.HIGHEST, Range.fixed(i.version()));
    // TODO Make resolveArtifact accept both Resolve and Imported
    resolveArtifact(sourceReference, session, repositories, originalResolvedThings, imported);
    Artifact artifact = new DefaultArtifact(i.group(), i.artifact(), BILL_OF_MATERIALS, DEPS, i.version());

    ArtifactResult artifactResult = repositorySystem.resolveArtifact(session, new ArtifactRequest().setArtifact(artifact));

    RangeResolver importedRangeResolver = RangeResolverProcessor.parseRangeResolver(i.gav(), new FileReader(artifactResult.getArtifact().getFile()));
    return importedRangeResolver;
  }

  private Resolved resolveArtifact(
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories,
      Map<String, Resolved> originalResolvedThings,
      Resolve resolve) {
    try {

      Artifact artifact = resolveToArtifact(resolve);

      VersionRangeRequest rangeRequest = new VersionRangeRequest();
      rangeRequest.setArtifact(artifact);
      rangeRequest.setRepositories(repositories);

      List<Version> releasesOnly =
          repositorySystem.resolveVersionRange(session, rangeRequest).getVersions().stream().filter(this::excludeSnapshots).collect(Collectors.toList());

      if (releasesOnly.isEmpty()) {
        throw missingReleases(resolve, sourceReference);
      }

      Version boundVersion = resolve.bounds() == Bound.HIGHEST ? releasesOnly.get(releasesOnly.size() - 1) : releasesOnly.get(0);

      if (boundVersion != null) {

        Resolved resolved = Resolved.of("", resolve.group(), resolve.artifact(), boundVersion.toString(), sourceReference, resolve.propertyName());

        // was the resolved item already resolved? and if so, only return the new resolution if newer, otherwise keep the original with it's original
        // resolved from lines.
        if (originalResolvedThings.containsKey(resolved.key())) {
          Resolved originalResolution = originalResolvedThings.get(resolved.key());
          if (!isNewerVersion(resolved.version(), originalResolution.version())) {
            return originalResolution;
          }
        }

        return resolved;
      } else {
        throw missingReleases(resolve, sourceReference);
      }

    } catch (RepositoryException e) {
      warningLogger.accept(e.getMessage());
      return null;
    }
  }

  public static boolean isNewerVersion(String first, String second) {
    DefaultArtifactVersion vF = new DefaultArtifactVersion(first);
    DefaultArtifactVersion vS = new DefaultArtifactVersion(second);
    return vF.compareTo(vS) > 0;
  }

  private boolean excludeSnapshots(Version v) {
    return !v.toString().contains("-SNAPSHOT");
  }

  private IllegalStateException missingReleases(Resolve resolve, String sourceReference) {
    return new IllegalStateException(
        String.format("Unable to resolve any releases of %s in range of %s from %s", resolve.artifact(), resolve.range().asVersionRange(), sourceReference));
  }

  private DefaultArtifact resolveToArtifact(Resolve resolve) {
    return new DefaultArtifact(String.format("%s:%s:%s", resolve.group(), resolve.artifact(), resolve.range().asVersionRange()));
  }

  private boolean isLocked(String sourceReference, Map<String, Resolved> lockedProperties, Resolve resolve) {
    Resolved locked = lockedProperties.get(resolve.key());
    if (locked != null && locked.resolvedFrom().equals(sourceReference)) {
      infoLogger.accept(String.format("Skipping resolution of %s, artifact is locally locked.", resolve.artifact()));
      return true;
    }
    if (locked != null && !locked.resolvedFrom().equals(sourceReference)) {
      infoLogger.accept(String.format("Re-resolving %s, artifact was originally locked from %s.", resolve.artifact(), locked.resolvedFrom()));
      return false;
    }
    return false;
  }

  private MetadataRequest buildMetaDataRequest(AbstractResolve rep) {
    return new MetadataRequest(new DefaultMetadata(rep.group(), rep.artifact(), Metadata.Nature.RELEASE));
  }

  private RemoteRepository buildRepository(AbstractRepository repo, boolean updateSnapshots) {
    String updatePolicy = updateSnapshots ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_DAILY;
    return new RemoteRepository.Builder(repo.url(), "default", repo.url())
        .setPolicy(new RepositoryPolicy(true, updatePolicy, RepositoryPolicy.CHECKSUM_POLICY_WARN))
        .build();
  }
}
