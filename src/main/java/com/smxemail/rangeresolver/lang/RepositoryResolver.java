package com.smxemail.rangeresolver.lang;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.smxemail.rangeresolver.api.AbstractRepository;
import com.smxemail.rangeresolver.api.AbstractResolve;
import com.smxemail.rangeresolver.api.Bound;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
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
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.emptyToNull;

public class RepositoryResolver {

  public static final String BILL_OF_MATERIALS = "bill-of-materials";

  public static final String DEPS = "deps";

  public static final Pattern FILTER_SNAPSHOTS = Pattern.compile("^.*-SNAPSHOT$");

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

    // TODO Should import against a specified repository - rather than all?
    Collection<Repository> repositoryImmutableCollection = rangeResolver.repositories().values();
    List<RemoteRepository> repositories = buildeRemoteRepositories(repositoryImmutableCollection);

    repositorySystem.newResolutionRepositories(session, repositories);

    ResolutionResult resolvedImportedThings =
        rangeResolver
            .imports()
            .stream()
            .map(imported -> processImport(sourceReference, session, repositories, previouslyImportedGavs, imported))
            .distinct()
            .collect(ResolutionResult.REDUCING);

    Map<String, Resolved> originallyLockedThings = resolvedImportedThings.locked().stream().collect(Resolved.collectByKey);
    originallyLockedThings.putAll(rangeResolver.locked().stream().collect(Resolved.collectByKey));

    Map<String, Resolved> locallyResolvedThings = rangeResolver.resolved().stream().collect(Resolved.collectByKey);
    Map<String, Resolved> originallyResolvedThings = resolvedImportedThings.resolved().stream().collect(Resolved.collectByKey);

    Map<String, Resolve> toResolve = Resolve.mapByKey(resolvedImportedThings.resolves());
    toResolve.putAll(Resolve.mapByKey(rangeResolver.resolves()));

    List<MetadataRequest> metadataRequests = toResolve.values().stream().map(this::buildMetaDataRequest).collect(Collectors.toList());

    infoLogger.accept("Resolving metadata for " + sourceReference);
    repositorySystem.resolveMetadata(session, metadataRequests);

    originallyResolvedThings.putAll(originallyLockedThings);

    Map<String, Resolved> resolvedThings =
        toResolve
            .values()
            .stream()
            .filter(resolve -> !isLocked(sourceReference, originallyLockedThings, resolve))
            .flatMap(
                resolve ->
                    optionalStream(
                        resolveArtifact(
                            sourceReference,
                            session,
                            buildeRemoteRepositories(rangeResolver.repositories().get(resolve.resolutionGrouping())),
                            originallyResolvedThings,
                            resolve)))
            .collect(
                () -> {
                  Map<String, Resolved> resolution = new TreeMap<>(Comparator.naturalOrder());
                  resolution.putAll(locallyResolvedThings);
                  resolution.putAll(originallyLockedThings);
                  resolution.putAll(originallyResolvedThings);
                  return resolution;
                },
                (resolution, resolved) -> {
                  Resolved existingResolved = resolution.get(resolved.key());
                  if (existingResolved == null) {
                    resolution.put(resolved.key(), resolved);
                  } else if (existingResolved.version().equals(resolved.version())) {
                    resolution.put(existingResolved.key(), existingResolved);
                  } else {
                    resolution.put(resolved.key(), resolved);
                  }
                },
                Map::putAll);

    List<Resolved> sortedUniqueResolved = ImmutableList.copyOf(resolvedThings.values());

    warnAboutResolutionConflicts(sortedUniqueResolved);

    return ResolutionResult.of(resolvedImportedThings.aliases(), rangeResolver.resolves(), sortedUniqueResolved, rangeResolver.locked());
  }

  private List<RemoteRepository> buildeRemoteRepositories(Collection<Repository> repositoryImmutableCollection) {
    return repositoryImmutableCollection.stream().map(repo -> buildRepository(repo, updateSnapshots)).collect(Collectors.toList());
  }

  private <T> Stream<T> optionalStream(Optional<T> value) {
    return value.isPresent() ? Stream.of(value.get()) : Stream.empty();
  }

  /**
   * Group a list of resolved artifacts by their groupId:artifactId, warn if multiple entries are found.
   *
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
      String sourceReference, RepositorySystemSession session, List<RemoteRepository> repositories, List<String> previousImports, Import i) {

    if (previousImports.contains(i.gav())) {
      throw new IllegalStateException(String.format("Circular imports detected for %s in %s", i.gav(), sourceReference));
    } else {
      try {
        RangeResolver importedRangeResolver = resolveImport(sourceReference, session, repositories, i);
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

  public RangeResolver resolveImport(String sourceReference, RepositorySystemSession session, List<RemoteRepository> repositories, Import i)
      throws ArtifactResolutionException, IOException {

    Bound importBound = i.version().endsWith("-SNAPSHOT") ? Bound.SNAPSHOT : Bound.HIGHEST;
    Resolve imported =
        Resolve.of("", i.group(), i.artifact(), BILL_OF_MATERIALS, DEPS, importBound, Range.fixed(i.version()), Optional.empty(), sourceReference, "imported");

    // TODO Make resolveArtifact accept both Resolve and Imported
    try {
      resolveArtifact(sourceReference, session, repositories, Collections.emptyMap(), imported);
    } catch (IllegalStateException ignored) {
      // ignore
    }

    Artifact artifact = new DefaultArtifact(i.group(), i.artifact(), BILL_OF_MATERIALS, DEPS, i.version());
    File artifactFile;

    LocalArtifactRequest lar = new LocalArtifactRequest(artifact, repositories, "");
    LocalArtifactResult localArtifactResult = session.getLocalRepositoryManager().find(session, lar);
    artifactFile = localArtifactResult.getFile();
    if (artifactFile == null) {
      throw new IllegalArgumentException("Unable to resolve " + artifact.toString() + " in either local or remote repositories.");
    }

    RangeResolver importedRangeResolver = RangeResolverProcessor.parseRangeResolver(i.gav(), new FileReader(artifactFile));

    if (i.unlocked()) {
      importedRangeResolver = unlockDependencies(importedRangeResolver);
    }

    return importedRangeResolver;
  }

  public static RangeResolver unlockDependencies(RangeResolver importedRangeResolver) {
    List<Resolved> allResolved = new ArrayList<>();
    allResolved.addAll(importedRangeResolver.locked());
    allResolved.addAll(importedRangeResolver.resolved());

    importedRangeResolver = importedRangeResolver.withResolved(allResolved).withLocked();
    return importedRangeResolver;
  }

  private Optional<Resolved> resolveArtifact(
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories,
      Map<String, Resolved> originalResolvedThings,
      Resolve resolve) {
    try {

      Resolved originalResolution = originalResolvedThings.get(resolve.key());

      Optional<Range> resolvedVersionRange = resolveVersionRange(session, repositories, resolve);

      if (!resolvedVersionRange.isPresent()) {
        String repositorySource = repositories.stream().map(RemoteRepository::toString).collect(Collectors.joining("|", "[", "]"));

        String baseError =
            String.format(
                "Unable to resolve %s releases in the range %s from %s, declared in %s",
                resolve.key(), resolve.range().asVersionRange(), repositorySource, sourceReference);
        if (originalResolution != null) {
          warningLogger.accept(baseError + String.format(": using existing resolution of %s.", originalResolution.version()));
          return Optional.of(originalResolution);
        } else {
          warningLogger.accept(baseError);
          return Optional.empty();
        }

      } else {
        Range constrainedRange = resolvedVersionRange.get();
        String boundVersion = resolve.bounds() == Bound.LOWEST ? constrainedRange.lower() : constrainedRange.upper();

        Resolved resolved = Resolved.of("", resolve.group(), resolve.artifact(), boundVersion, sourceReference, resolve.propertyName(), constrainedRange);

        // was the resolved item already resolved? and if so, only return the new resolution if newer, otherwise keep the original with it's original
        // resolved from lines.
        if (originalResolvedThings.containsKey(resolved.key())) {
          if (!isNewerVersion(resolved.version(), originalResolution.version()) && sourceReference.equals(originalResolution.resolvedFrom())) {
            return Optional.of(originalResolution);
          }
        }

        return Optional.of(resolved);
      }

    } catch (RepositoryException e) {
      warningLogger.accept(e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<Range> resolveVersionRange(RepositorySystemSession session, List<RemoteRepository> repositories, Resolve resolve)
      throws VersionRangeResolutionException, ArtifactResolutionException {

    Artifact artifact = resolveToArtifact(resolve);
    VersionRangeRequest rangeRequest = new VersionRangeRequest();
    rangeRequest.setArtifact(artifact);
    rangeRequest.setRepositories(repositories);

    List<Version> resolvedVersions =
        repositorySystem.resolveVersionRange(session, rangeRequest).getVersions().stream().filter(filterVersion(resolve)).collect(Collectors.toList());

    if (resolvedVersions.isEmpty()) {
      return Optional.empty();
    } else {
      Range resolvedRange = resolveBoundRange(resolvedVersions);
      Set<ArtifactRequest> resolvedArtifactRequests = resovleArtifactRequests(repositories, resolve, resolvedRange);
      repositorySystem.resolveArtifacts(session, resolvedArtifactRequests);
      return Optional.of(resolvedRange);
    }
  }

  private ImmutableSet<ArtifactRequest> resovleArtifactRequests(List<RemoteRepository> repositories, Resolve resolve, Range range) {
    Artifact lowerArtifact = new DefaultArtifact(resolve.group(), resolve.artifact(), resolve.classifier(), resolve.extension(), range.lower());
    Artifact upperAritifact = new DefaultArtifact(resolve.group(), resolve.artifact(), resolve.classifier(), resolve.extension(), range.upper());

    return ImmutableSet.of(new ArtifactRequest(lowerArtifact, repositories, ""), new ArtifactRequest(upperAritifact, repositories, ""));
  }

  private Range resolveBoundRange(List<Version> versions) {
    if (versions.size() == 1) {
      return Range.fixed(versions.get(0).toString());
    } else {
      return Range.openUpper(versions.get(0).toString(), versions.get(versions.size() - 1).toString());
    }
  }

  public static boolean isNewerVersion(String first, String second) {
    DefaultArtifactVersion vF = new DefaultArtifactVersion(first);
    DefaultArtifactVersion vS = new DefaultArtifactVersion(second);
    return vF.compareTo(vS) > 0;
  }

  private Predicate<Version> filterVersion(Resolve resolve) {
    return v -> !resolve.filtering().orElse(FILTER_SNAPSHOTS).matcher(v.toString()).matches();
  }

  private DefaultArtifact resolveToArtifact(Resolve resolve) {
    return new DefaultArtifact(resolve.group(), resolve.artifact(), emptyToNull(resolve.classifier()), resolve.extension(), resolve.range().asVersionRange());
  }

  private boolean isLocked(String sourceReference, Map<String, Resolved> lockedProperties, Resolve resolve) {
    Resolved locked = lockedProperties.get(resolve.key());
    if (locked != null && locked.resolvedFrom().equals(sourceReference)) {
      return true;
    }
    if (locked != null && !locked.resolvedFrom().equals(sourceReference)) {
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
