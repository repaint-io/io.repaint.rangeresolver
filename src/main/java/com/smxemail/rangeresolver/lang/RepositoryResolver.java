package com.smxemail.rangeresolver.lang;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.smxemail.rangeresolver.api.AbstractRepository;
import com.smxemail.rangeresolver.api.AbstractResolve;
import com.smxemail.rangeresolver.api.Allow;
import com.smxemail.rangeresolver.api.Bound;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import one.util.streamex.StreamEx;
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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.emptyToNull;
import static com.smxemail.rangeresolver.lang.Memoizer.memoize;
import static com.smxemail.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.ALLOW_SNAPSHOTS;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.FILTER_SNAPSHOTS;
import static java.util.stream.Collectors.toList;

public class RepositoryResolver {

  public static final String BILL_OF_MATERIALS = "bill-of-materials";

  public static final String DEPS = "deps";

  private Consumer<String> infoLogger;
  private Consumer<String> warningLogger;

  private String localRepoDir;

  private boolean updateSnapshots;

  private RepositorySystem repositorySystem;

  private RepositoryResolver(
      Consumer<String> infoLogger,
      Consumer<String> warningLogger,
      String localRepoDir,
      boolean updateSnapshots) {
    this.infoLogger = infoLogger;
    this.warningLogger = warningLogger;
    this.localRepoDir = localRepoDir;
    this.updateSnapshots = updateSnapshots;
    this.repositorySystem = newRepositorySystem();
  }

  public static RepositoryResolver createRepositoryResolver(
      Consumer<String> infoLogger,
      Consumer<String> warningLogger,
      String localRepoDir,
      boolean updateSnapshots) {
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
    session.setLocalRepositoryManager(
        repositorySystem.newLocalRepositoryManager(session, localRepo));
    return session;
  }

  /**
   * Resolve artifact requests from the resolution model.
   *
   * @param rangeResolver The range resolution model.
   * @return A list of resolved artifacts
   * @throws RepositoryException
   */
  public ResolutionResult resolveRanges(
      RangeResolver rangeResolver, String resolverReference, String sourceReference)
      throws RepositoryException {
    RepositorySystemSession session = newRepositorySystemSession();

    List<RemoteRepository> repositories = buildeRepositories(rangeResolver.repositories().values());

    repositorySystem.newResolutionRepositories(session, repositories);

    List<RangeResolver> importedResolvers =
        parseImports(rangeResolver, sourceReference, session, repositories);

    Map<String, Resolved> importedArtifacts =
        importedResolvers
            .stream()
            .flatMap(this::streamResolvedArtifacts)
            .collect(Resolved.collectByKey);

    Map<String, Resolve> resolvesFromLocal =
        rangeResolver.resolves().stream().collect(Resolve.collectByKey);

    Map<String, Resolved> lockedFromLocal =
        rangeResolver.locked().stream().collect(Resolved.collectByKey);

    List<Resolve> resolvesFromImportedArtifacts =
        importedResolvers.stream().flatMap(this::streamResolvesArtifacts).collect(toList());

    infoLogger.accept("Resolving metadata for " + resolverReference);
    List<Resolve> toResolve =
        ImmutableList.<Resolve>builder()
            .addAll(resolvesFromImportedArtifacts)
            .addAll(rangeResolver.resolves())
            .build();

    List<MetadataRequest> metadataRequests =
        toResolve.stream().map(this::buildMetaDataRequest).collect(toList());

    repositorySystem.resolveMetadata(session, metadataRequests);

    Function<Resolve, List<RemoteRepository>> buildRepositories =
        memoize(
            resolve ->
                buildeRepositories(rangeResolver.repositories().get(resolve.resolutionGrouping())));

    Function<Resolve, Optional<Resolved>> resolveArtifact =
        memoize(
            resolve ->
                resolveArtifact(
                    resolverReference, session, buildRepositories.apply(resolve), resolve));

    Predicate<Resolve> isUpdatedAllowed = matchesAnyAllowPattern(rangeResolver);
    Predicate<Resolve> isLocallyDeclared = resolve -> resolvesFromLocal.containsKey(resolve.key());
    Predicate<Resolve> isUpdatedVersion =
        resolve ->
            resolveArtifact
                .apply(resolve)
                .map(
                    resolved -> isNewerVersion(resolved, importedArtifacts.get(resolve.key()))
                        && isNewerVersion(resolved, lockedFromLocal.get(resolve.key())))
                .orElse(false);

    Predicate<Resolve> isResolvable =
        resolve ->
            ((isUpdatedAllowed.test(resolve)) && isUpdatedVersion.test(resolve))
                || isLocallyDeclared.test(resolve);

    Map<String, Resolved> resolvedThings =
        toResolve
            .stream()
            .filter(isResolvable)
            .flatMap(r -> StreamEx.of(resolveArtifact.apply(r)))
            .collect(Resolved.collectByKey);

    List<Resolved> sortedUniqueResolved = sortResolvedThings(resolvedThings.values());

    Map<String, Resolved> localArtifacts = new HashMap<>(importedArtifacts);
    localArtifacts.putAll(lockedFromLocal);

    List<Resolved> sortedUniqueLocked =
        sortResolvedThings(
            localArtifacts
                .values()
                .stream()
                .filter(r -> !resolvedThings.containsKey(r.key()))
                .collect(toList()));

    warnAboutResolutionConflicts(sortedUniqueResolved);

    return ResolutionResult.of(
        rangeResolver.aliases(),
        rangeResolver.resolves(),
        sortedUniqueResolved,
        sortedUniqueLocked);
  }

  private Predicate<Resolve> matchesAnyAllowPattern(RangeResolver rangeResolver) {
    Predicate<String> predicate = matchesAny(allowPatterns(rangeResolver));
    return resolve -> predicate.test(resolve.key());
  }

  private List<RangeResolver> parseImports(
      RangeResolver rangeResolver,
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories) {
    return rangeResolver
        .imports()
        .stream()
        .map(i -> parseImport(sourceReference, session, repositories, i))
        .collect(toList());
  }

  private Stream<? extends Resolved> streamResolvedArtifacts(RangeResolver resolver) {
    return Stream.concat(resolver.resolved().stream(), resolver.locked().stream());
  }

  private Stream<? extends Resolve> streamResolvesArtifacts(RangeResolver resolver) {
    return resolver.resolves().stream();
  }

  public static List<Resolved> sortResolvedThings(Collection<Resolved> values) {
    return StreamEx.of(values).sortedBy(Resolved::key).toList();
  }

  private List<RemoteRepository> buildeRepositories(Collection<Repository> repositories) {
    return repositories
        .stream()
        .map(repo -> buildRepository(repo, updateSnapshots))
        .collect(toList());
  }

  /**
   * Group a list of resolved artifacts by their groupId:artifactId, warn if multiple entries are
   * found.
   *
   * @param resolved A list of {@link Resolved} items.
   */
  private void warnAboutResolutionConflicts(List<Resolved> resolved) {
    Map<String, List<Resolved>> conflicting =
        resolved.stream().collect(Collectors.groupingBy(Resolved::key));
    for (Map.Entry<String, List<Resolved>> entry : conflicting.entrySet()) {
      if (entry.getValue().size() > 1) {
        String resolutionGavs =
            entry.getValue().stream().map(Resolved::resolvedFrom).collect(Collectors.joining(", "));
        warningLogger.accept(
            String.format(
                "Duplicate resolution of %s found in: %s", entry.getKey(), resolutionGavs));
      }
    }
  }

  public static List<Pattern> allowPatterns(RangeResolver rangeResolver) {
    return rangeResolver.allows().stream().map(Allow::pattern).collect(Collectors.toList());
  }

  private RangeResolver parseImport(
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories,
      Import i) {

    boolean requestSnapshots = i.version().endsWith("-SNAPSHOT");
    Bound importBound = requestSnapshots ? Bound.SNAPSHOT : Bound.HIGHEST;
    Pattern importFilter = requestSnapshots ? ALLOW_SNAPSHOTS : FILTER_SNAPSHOTS;
    Resolve imported =
        Resolve.of(
            "",
            i.group(),
            i.artifact(),
            BILL_OF_MATERIALS,
            DEPS,
            importBound,
            Range.fixed(i.version()),
            importFilter,
            sourceReference,
            "imported");

    // TODO Make resolveArtifact accept both Resolve and Imported
    try {
      resolveArtifact(sourceReference, session, repositories, imported);
    } catch (IllegalStateException ignored) {
      // ignore
    }

    Artifact artifact =
        new DefaultArtifact(i.group(), i.artifact(), BILL_OF_MATERIALS, DEPS, i.version());
    File artifactFile;

    LocalArtifactRequest lar = new LocalArtifactRequest(artifact, repositories, "");
    LocalArtifactResult localArtifactResult =
        session.getLocalRepositoryManager().find(session, lar);
    artifactFile = localArtifactResult.getFile();
    if (artifactFile == null) {
      throw new IllegalArgumentException(
          "Unable to resolve " + artifact.toString() + " in either local or remote repositories.");
    }

    try {
      RangeResolver resolver = parseRangeResolver(i.gav(), new FileReader(artifactFile));
      return  snapshotDependencies(resolver, warningLogger, i.snapshots());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static RangeResolver unlockDependencies(RangeResolver resolver, List<Pattern> unlocked) {
    Predicate<Resolved> isMatchingUnlocked = resolved -> matchesAny(unlocked).test(resolved.key());
    List<Resolved> allResolved = new ArrayList<>();

    allResolved.addAll(resolver.locked().stream().filter(isMatchingUnlocked).collect(toList()));

    List<Resolved> locked =
        resolver.locked().stream().filter(isMatchingUnlocked.negate()).collect(toList());

    allResolved.addAll(resolver.resolved());

    return resolver
        .withResolved(sortResolvedThings(allResolved))
        .withLocked(sortResolvedThings(locked));
  }

  public static RangeResolver snapshotDependencies(
      RangeResolver importedRangeResolver,
      Consumer<String> warningLogger,
      List<Pattern> snapshots) {
    Predicate<Resolve> isMatchingSnapshot = resolve -> matchesAny(snapshots).test(resolve.key());
    Predicate<Resolved> isNonMatchingResolvedSnapshot =
        resolve -> !matchesAny(snapshots).test(resolve.key());

    List<Resolve> snapshotResolves =
        StreamEx.of(importedRangeResolver.resolves())
            .map(
                r -> {
                  if (isMatchingSnapshot.test(r)) {
                    warningLogger.accept("Allowing SNAPSHOT resolution of " + r.key());
                    return r.withBounds(Bound.SNAPSHOT).withFiltering(ALLOW_SNAPSHOTS);
                  } else {
                    return r;
                  }
                })
            .toList();

    List<Resolved> lockedWithoutSnapshots =
        StreamEx.of(importedRangeResolver.locked()).filter(isNonMatchingResolvedSnapshot).toList();

    importedRangeResolver =
        importedRangeResolver.withResolves(snapshotResolves).withLocked(lockedWithoutSnapshots);
    return importedRangeResolver;
  }

  private static Predicate<String> matchesAny(List<Pattern> patterns) {
    return value -> patterns.stream().anyMatch(pattern -> pattern.matcher(value).matches());
  }

  private Optional<Resolved> resolveArtifact(
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories,
      Resolve resolve) {
    try {

      Optional<Range> resolvedVersionRange = resolveVersionRange(session, repositories, resolve);

      if (!resolvedVersionRange.isPresent()) {
        String repositorySource =
            repositories
                .stream()
                .map(RemoteRepository::toString)
                .collect(Collectors.joining("|", "[", "]"));

        String baseError =
            String.format(
                "Unable to resolve %s releases in the range %s from %s, declared in %s",
                resolve.key(), resolve.range().asVersionRange(), repositorySource, sourceReference);

        warningLogger.accept(baseError);
        return Optional.empty();

      } else {
        Range constrainedRange = resolvedVersionRange.get();
        String boundVersion =
            resolve.bounds() == Bound.LOWEST ? constrainedRange.lower() : constrainedRange.upper();

        Resolved resolved =
            Resolved.of(
                "",
                resolve.group(),
                resolve.artifact(),
                boundVersion,
                sourceReference,
                resolve.propertyName(),
                constrainedRange);

        return Optional.of(resolved);
      }

    } catch (RepositoryException e) {
      warningLogger.accept(e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<Range> resolveVersionRange(
      RepositorySystemSession session, List<RemoteRepository> repositories, Resolve resolve)
      throws VersionRangeResolutionException, ArtifactResolutionException {

    Artifact artifact = resolveToArtifact(resolve);
    VersionRangeRequest rangeRequest = new VersionRangeRequest();
    rangeRequest.setArtifact(artifact);
    rangeRequest.setRepositories(repositories);

    List<Version> resolvedVersions =
        StreamEx.of(repositorySystem.resolveVersionRange(session, rangeRequest).getVersions())
            .filter(filterVersion(resolve))
            .toList();

    if (resolvedVersions.isEmpty()) {
      return Optional.empty();
    } else {
      Range resolvedRange = resolveBoundRange(resolvedVersions);
      Set<ArtifactRequest> resolvedArtifactRequests =
          resovleArtifactRequests(repositories, resolve, resolvedRange);
      repositorySystem.resolveArtifacts(session, resolvedArtifactRequests);
      return Optional.of(resolvedRange);
    }
  }

  private ImmutableSet<ArtifactRequest> resovleArtifactRequests(
      List<RemoteRepository> repositories, Resolve resolve, Range range) {
    Artifact lowerArtifact =
        new DefaultArtifact(
            resolve.group(),
            resolve.artifact(),
            resolve.classifier(),
            resolve.extension(),
            range.lower());
    Artifact upperAritifact =
        new DefaultArtifact(
            resolve.group(),
            resolve.artifact(),
            resolve.classifier(),
            resolve.extension(),
            range.upper());

    return ImmutableSet.of(
        new ArtifactRequest(lowerArtifact, repositories, ""),
        new ArtifactRequest(upperAritifact, repositories, ""));
  }

  private Range resolveBoundRange(List<Version> versions) {
    if (versions.size() == 1) {
      return Range.fixed(versions.get(0).toString());
    } else {
      return Range.openUpper(
          versions.get(0).toString(), versions.get(versions.size() - 1).toString());
    }
  }

  public static boolean isNewerVersion(String first, String second) {
    if (second == null && first != null) {
      return true;
    }
    if (first == null) {
      return false;
    }

    DefaultArtifactVersion vF = new DefaultArtifactVersion(first);
    DefaultArtifactVersion vS = new DefaultArtifactVersion(second);
    return vF.compareTo(vS) > 0;
  }

  public static boolean isNewerVersion(Resolved first, Resolved second) {
    if (second == null && first != null) {
      return true;
    }
    if (first == null) {
      return false;
    }

    return isNewerVersion(first.version(), second.version());
  }

  private Predicate<Version> filterVersion(Resolve resolve) {
    return v -> !resolve.filtering().matcher(v.toString()).matches();
  }

  private DefaultArtifact resolveToArtifact(Resolve resolve) {
    return new DefaultArtifact(
        resolve.group(),
        resolve.artifact(),
        emptyToNull(resolve.classifier()),
        resolve.extension(),
        resolve.range().asVersionRange());
  }

  private MetadataRequest buildMetaDataRequest(AbstractResolve rep) {
    return new MetadataRequest(
        new DefaultMetadata(rep.group(), rep.artifact(), Metadata.Nature.RELEASE));
  }

  private RemoteRepository buildRepository(AbstractRepository repo, boolean updateSnapshots) {
    String updatePolicy =
        updateSnapshots
            ? RepositoryPolicy.UPDATE_POLICY_ALWAYS
            : RepositoryPolicy.UPDATE_POLICY_DAILY;
    return new RemoteRepository.Builder(repo.url(), "default", repo.url())
        .setPolicy(new RepositoryPolicy(true, updatePolicy, RepositoryPolicy.CHECKSUM_POLICY_WARN))
        .build();
  }
}
