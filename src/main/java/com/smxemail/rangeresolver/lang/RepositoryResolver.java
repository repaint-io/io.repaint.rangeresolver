package com.smxemail.rangeresolver.lang;

import com.google.common.collect.ImmutableList;
import com.smxemail.rangeresolver.api.AbstractRepository;
import com.smxemail.rangeresolver.api.AbstractResolve;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
   * @return A list of resolved artifacts
   * @throws RepositoryException
   */
  public List<Resolved> resolveRanges(RangeResolver rangeResolver, String sourceReference) throws RepositoryException {
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
  public List<Resolved> resolveRanges(List<String> previouslyImportedGavs, RangeResolver rangeResolver, String sourceReference) throws RepositoryException {
    RepositorySystemSession session = newRepositorySystemSession();

    List<RemoteRepository> repositories =
        rangeResolver.repositories().stream().map(repo -> buildRepository(repo, updateSnapshots)).collect(Collectors.toList());

    repositorySystem.newResolutionRepositories(session, repositories);

    List<Resolved> resolvedImportedThings =
        rangeResolver
            .imports()
            .stream()
            .flatMap(imported -> resolveImport(sourceReference, session, repositories, previouslyImportedGavs, imported))
            .distinct()
            .collect(Collectors.toList());

    List<MetadataRequest> metadataRequests = rangeResolver.resolves().stream().map(rep -> buildMetaDataRequest(rep)).collect(Collectors.toList());

    infoLogger.accept("Resolving metadata....");
    repositorySystem.resolveMetadata(session, metadataRequests);

    List<String> lockedProperties = rangeResolver.locked().stream().map(Resolved::key).collect(Collectors.toList());

    List<Resolved> resolvedThings =
        rangeResolver
            .resolves()
            .stream()
            .filter(resolve -> isNotLocked(lockedProperties, resolve))
            .map(resolve -> resolveArtifact(sourceReference, session, repositories, resolve))
            .collect(Collectors.toList());

    Set<Resolved> allResolved = new TreeSet<>(Comparator.comparing(Resolved::key));
    allResolved.addAll(resolvedImportedThings);
    allResolved.addAll(resolvedThings);
    List<Resolved> sortedUniqueResolved = ImmutableList.copyOf(allResolved);

    warnAboutResolutionConflicts(sortedUniqueResolved);

    return sortedUniqueResolved;
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

  private Stream<? extends Resolved> resolveImport(
      String sourceReference, RepositorySystemSession session, List<RemoteRepository> repositories, List<String> previousImports, Import i) {

    if (previousImports.contains(i.gav())) {
      throw new IllegalStateException(String.format("Circular imports detected for %s in %s", i.gav(), sourceReference));
    } else {
      Resolve imported = Resolve.of("", i.group(), i.artifact(), AbstractResolve.Bound.HIGHEST, Range.fixed(i.version()), "");

      // TODO Make resolveArtifact accept both Resolve and Imported
      resolveArtifact(sourceReference, session, repositories, imported);

      try {

        Artifact artifact = new DefaultArtifact(i.group(), i.artifact(), BILL_OF_MATERIALS, DEPS, i.version());

        ArtifactResult artifactResult = repositorySystem.resolveArtifact(session, new ArtifactRequest().setArtifact(artifact));

        RangeResolver importedRangeResolver = RangeResolverProcessor.parseRangeResolver(i.gav(), new FileReader(artifactResult.getArtifact().getFile()));

        List<String> updatedImportGavs = ImmutableList.<String>builder().addAll(previousImports).add(i.gav()).build();

        return resolveRanges(updatedImportGavs, importedRangeResolver, i.gav()).stream();

      } catch (RepositoryException | IOException e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        warningLogger.accept(sw.toString());
        return Stream.empty();
      }
    }
  }

  private Resolved resolveArtifact(String sourceReference, RepositorySystemSession session, List<RemoteRepository> repositories, Resolve resolve) {
    try {

      Artifact artifact = resolveToArtifact(resolve);

      VersionRangeRequest rangeRequest = new VersionRangeRequest();
      rangeRequest.setArtifact(artifact);
      rangeRequest.setRepositories(repositories);

      List<Version> releasesOnly =
          repositorySystem.resolveVersionRange(session, rangeRequest).getVersions().stream().filter(this::excludeSnapshots).collect(Collectors.toList());

      if (releasesOnly.isEmpty()) {
        throw missingReleases(resolve);
      }

      Version boundVersion = resolve.bounds() == AbstractResolve.Bound.HIGHEST ? releasesOnly.get(releasesOnly.size() - 1) : releasesOnly.get(0);

      if (boundVersion != null) {

        Resolved resolved = Resolved.of("", resolve.group(), resolve.artifact(), boundVersion.toString(), sourceReference, resolve.propertyName());

        return resolved;
      } else {
        throw missingReleases(resolve);
      }

    } catch (RepositoryException e) {
      warningLogger.accept(e.getMessage());
      return null;
    }
  }

  private boolean excludeSnapshots(Version v) {
    return !v.toString().contains("-SNAPSHOT");
  }

  private IllegalStateException missingReleases(Resolve resolve) {
    return new IllegalStateException(
        String.format("Unable to resolve any releases in range of %s from %s", resolve.artifact(), resolve.range().asVersionRange()));
  }

  private DefaultArtifact resolveToArtifact(Resolve resolve) {
    return new DefaultArtifact(String.format("%s:%s:%s", resolve.group(), resolve.artifact(), resolve.range().asVersionRange()));
  }

  private boolean isNotLocked(List<String> lockedProperties, Resolve resolve) {
    boolean locked = lockedProperties.contains(resolve.key());
    if (locked) {
      infoLogger.accept(String.format("Skipping resolution of %s, artifact is locked.", resolve.artifact()));
    }
    return !locked;
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
