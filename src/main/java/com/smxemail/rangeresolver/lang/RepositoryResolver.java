package com.smxemail.rangeresolver.lang;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;

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
import org.eclipse.aether.repository.LocalRepositoryManager;
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
import org.jspecify.annotations.Nullable;

import com.smxemail.rangeresolver.api.AbstractRepository;
import com.smxemail.rangeresolver.api.AbstractResolve;
import com.smxemail.rangeresolver.api.ArtifactType;
import com.smxemail.rangeresolver.api.Bound;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.ResolveType;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.api.ResolvedType;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Strings.emptyToNull;

import static io.vavr.API.List;
import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;

import static com.smxemail.rangeresolver.api.ArtifactType.MAVEN;
import static com.smxemail.rangeresolver.api.ResolvedType.BLACKLISTED;
import static com.smxemail.rangeresolver.api.ResolvedType.DEPRECATED;
import static com.smxemail.rangeresolver.api.ResolvedType.LOCKED;
import static com.smxemail.rangeresolver.api.ResolvedType.RESOLVED;
import static com.smxemail.rangeresolver.lang.Memoizer.memo;
import static com.smxemail.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.ALLOW_SNAPSHOTS;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.FILTER_SNAPSHOTS;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class RepositoryResolver {
  public static final String BILL_OF_MATERIALS = "bill-of-materials";

  public static final String DEPS = "deps";

  private Consumer<String> infoLogger;
  private Consumer<String> warningLogger;
  private String allowUnlocked;
  private String allowSnapshots;
  private boolean updateSnapshots;
  private RepositorySystem repositorySystem;
  private RepositorySystemSession session;

  private RepositoryResolver(
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      Consumer<String> infoLogger,
      Consumer<String> warningLogger,
      String allowUnlocked,
      String allowSnapshots,
      boolean updateSnapshots) {
    this.repositorySystem = repositorySystem;
    this.session = session;
    this.infoLogger = infoLogger;
    this.warningLogger = warningLogger;
    this.allowUnlocked = allowUnlocked;
    this.allowSnapshots = allowSnapshots;
    this.updateSnapshots = updateSnapshots;
    this.repositorySystem = repositorySystem;
  }

  public static RepositoryResolver createRepositoryResolver(
      Consumer<String> infoLogger,
      Consumer<String> warningLogger,
      String localRepoDir,
      String allowUnlocked,
      String allowSnapshots,
      boolean updateSnapshots) {
    RepositorySystem system = newRepositorySystem();
    return new RepositoryResolver(
        system,
        newRepositorySystemSession(system, localRepoDir),
        infoLogger,
        warningLogger,
        allowUnlocked,
        allowSnapshots,
        updateSnapshots);
  }

  public static RepositoryResolver createRepositoryResolver(
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      Consumer<String> infoLogger,
      Consumer<String> warningLogger,
      String allowUnlocked,
      String allowSnapshots,
      boolean updateSnapshots) {
    return new RepositoryResolver(
        repositorySystem, session, infoLogger, warningLogger, allowUnlocked, allowSnapshots, updateSnapshots);
  }

  private static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    return locator.getService(RepositorySystem.class);
  }

  private static RepositorySystemSession newRepositorySystemSession(
      RepositorySystem repositorySystem, String localRepoDir) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepo = new LocalRepository(localRepoDir);
    LocalRepositoryManager localRepositoryManager = requireNonNull(
        repositorySystem.newLocalRepositoryManager(session, localRepo), "Missing Local Repository Manager");
    session.setLocalRepositoryManager(localRepositoryManager);
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
      RangeResolver rangeResolver, String resolverReference, String sourceReference) {
    List<RemoteRepository> repositories =
        buildRepositories(rangeResolver.repositories().values());

    repositorySystem.newResolutionRepositories(session, repositories.toJavaList());

    List<RangeResolver> importedResolvers = parseImports(rangeResolver, sourceReference, session, repositories);

    Map<String, Resolved> importedArtifacts =
        importedResolvers.flatMap(this::streamResolvedArtifacts).collect(Resolved.collectByKey);

    List<Resolve> dockerResolves = ResolveType.DOCKER.from(rangeResolver);
    List<Resolve> resolvedResolves = ResolveType.RESOLVE.from(rangeResolver);
    List<Resolve> overRiddenResolves = ResolveType.OVERRIDE.from(rangeResolver);
    List<Resolve> deprecatedResolves = ResolveType.DEPRECATE.from(rangeResolver);
    List<Resolve> blacklistedResolves = ResolveType.BLACKLIST.from(rangeResolver);
    List<String> deprecatedKeysFromLocal = deprecatedResolves.map(Resolve::key);
    Map<String, Resolve> dockerResolvesFromLocal = dockerResolves.collect(Resolve.collectByKey);
    Map<String, Resolve> resolvesFromLocal = resolvedResolves.collect(Resolve.collectByKey);
    Map<String, Resolve> overridesFromLocal = overRiddenResolves.collect(Resolve.collectByKey);
    Map<String, Resolve> deprecatesFromLocal = deprecatedResolves.collect(Resolve.collectByKey);
    Map<String, Resolve> blacklistsFromLocal = blacklistedResolves.collect(Resolve.collectByKey);
    Map<String, Resolved> lockedFromLocal = collectAllLockedVersions(rangeResolver, overRiddenResolves);
    Map<String, Resolved> resolvedFromLocalForVersionChecking = collectAllResolvedVersions(rangeResolver);

    List<Resolve> imported = importedResolvers.flatMap(RangeResolver::resolves);

    checkForDuplicateResolutions(imported, resolvesFromLocal, blacklistsFromLocal, deprecatesFromLocal);

    Predicate<Resolve> isUpdatedAllowed = matchesAllowOrOverride(rangeResolver);

    infoLogger.accept("Resolving metadata for " + resolverReference);
    List<Resolve> toResolve = imported.filter(isUpdatedAllowed).appendAll(rangeResolver.resolves());

    resolveArtifacts(session, toResolve);

    Function<Resolve, List<RemoteRepository>> buildRepositories =
        memo(resolve -> buildRepositories(rangeResolver.repositories().get(resolve.grouping())));

    Function<Resolve, Option<Resolved>> resolveArtifact = memo(resolve -> {
      if (resolve.resolveType() == ResolveType.DOCKER) {
        return resolveDockerImage(resolve);
      } else {
        return resolveArtifact(
            resolvedFromLocalForVersionChecking,
            resolverReference,
            session,
            buildRepositories.apply(resolve),
            resolve);
      }
    });

    Predicate<Resolve> isLocallyDeclaredDocker = isLocallyDeclared(dockerResolvesFromLocal, overridesFromLocal);
    Predicate<Resolve> isLocallyDeclared = isLocallyDeclared(resolvesFromLocal, overridesFromLocal);
    Predicate<Resolve> isLocallyDeprecated = resolve -> deprecatesFromLocal.containsKey(resolve.key());
    Predicate<Resolve> isLocallyBlacklisted = resolve -> blacklistsFromLocal.containsKey(resolve.key());

    Predicate<Resolve> isUpdatedVersion = resolve -> resolveArtifact
        .apply(resolve)
        .map(resolved -> resolve.resolveType() == ResolveType.DOCKER
            || isNewerVersion(resolved, importedArtifacts.get(resolve.key()))
                && isNewerVersion(resolved, resolvedFromLocalForVersionChecking.get(resolve.key())))
        .getOrElse(false);

    Predicate<Resolve> isResolvable =
        isUpdatedAllowed.and(isUpdatedVersion).or(isLocallyDeclared).or(isLocallyDeclaredDocker);

    Map<String, Resolved> resolvedThings = toResolve
        .filter(isResolvable)
        .filter(r -> !deprecatedKeysFromLocal.contains(r.key()))
        .flatMap(resolveArtifact)
        .collect(Resolved.collectByKey);

    Map<String, Resolved> importedBlacklists = filterImported(importedArtifacts, BLACKLISTED);

    Map<String, Resolved> importedDeprecations = filterImported(importedArtifacts, DEPRECATED);

    Map<String, Resolved> localDeprecations =
        toResolve.filter(isLocallyDeprecated).flatMap(resolveArtifact).collect(Resolved.collectByKey);

    Map<String, Resolved> localBlacklists =
        toResolve.filter(isLocallyBlacklisted).flatMap(resolveArtifact).collect(Resolved.collectByKey);

    Map<String, Resolved> deprecatedThings = mergeMaps(importedDeprecations, localDeprecations);
    Map<String, Resolved> blacklistedThings = mergeMaps(importedBlacklists, localBlacklists);

    List<Resolved> sortedUniqueResolved = sortResolvedThings(resolvedThings.values());
    List<Resolved> sortedUniqueDeprecated = sortResolvedThings(deprecatedThings.values());
    List<Resolved> sortedUniqueBlacklisted = sortResolvedThings(blacklistedThings.values());
    List<Resolved> sortedUniqueLocked = resolveSortedUniqueLocked(
        importedArtifacts,
        lockedFromLocal,
        resolvedThings,
        blacklistedThings,
        deprecatedThings,
        sortedUniqueResolved);

    return ResolutionResult.of(
        rangeResolver.aliases(),
        rangeResolver.resolves(),
        sortedUniqueBlacklisted,
        sortedUniqueDeprecated,
        sortedUniqueResolved,
        sortedUniqueLocked);
  }

  private Option<Resolved> resolveDockerImage(Resolve resolve) {
    var imageName = resolve.artifact();

    try {
      var resolved = revolveNamedDockerImage(imageName);

      return resolved.map(image -> {
        var parts = image.split("@", 2);
        var artifact = parts[0];
        var version = parts[1];
        var range = Range.of("", version, version, "");
        return Resolved.of(
            "",
            ArtifactType.DOCKER,
            resolve.group(),
            artifact,
            version,
            none(),
            resolve.propertyName(),
            range,
            RESOLVED);
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void pullDockerImage(String image) throws IOException {
    var pullReader = new ProcessBuilder("docker", "pull", image)
        .redirectErrorStream(true)
        .start()
        .inputReader();

    pullReader.lines().forEach(infoLogger);
    pullReader.close();
  }

  private Option<String> revolveNamedDockerImage(String image) throws IOException {
    pullDockerImage(image);
    var reader = new ProcessBuilder("docker", "inspect", "--format={{index .RepoDigests 0}}", image)
        .redirectErrorStream(true)
        .start()
        .inputReader();

    var resolved = reader.lines().findFirst();
    reader.close();

    return Option.ofOptional(resolved);
  }

  private Map<String, Resolved> collectAllLockedVersions(
      RangeResolver rangeResolver, List<Resolve> overRiddenResolves) {
    List<String> overridenKeys = overRiddenResolves.map(Resolve::key);
    Map<String, Resolved> lockedFromLocal = rangeResolver
        .locked()
        .filter(r -> !overridenKeys.contains(r.key()))
        .collect(Resolved.collectByKey);
    return lockedFromLocal;
  }

  private Map<String, Resolved> collectAllResolvedVersions(RangeResolver rangeResolver) {
    Map<String, Resolved> resolvedFromLocalForVersionChecking = rangeResolver
        .blacklisted()
        .appendAll(rangeResolver.locked())
        .appendAll(rangeResolver.deprecated())
        .collect(Resolved.collectByKey);
    return resolvedFromLocalForVersionChecking;
  }

  private <K, V> Map<K, V> mergeMaps(Map<K, V> importedDeprecations, Map<K, V> localDeprecations) {
    Map<K, V> deprecatedThings = new HashMap<>();
    deprecatedThings.putAll(importedDeprecations);
    deprecatedThings.putAll(localDeprecations);
    return deprecatedThings;
  }

  private Map<String, Resolved> filterImported(Map<String, Resolved> importedArtifacts, ResolvedType blacklisted) {
    return io.vavr.collection.HashMap.ofAll(importedArtifacts)
        .filterValues(res -> res.resolutionType() == blacklisted)
        .toJavaMap();
  }

  private List<Resolved> resolveSortedUniqueLocked(
      Map<String, Resolved> importedArtifacts,
      Map<String, Resolved> lockedFromLocal,
      Map<String, Resolved> resolvedThings,
      Map<String, Resolved> blackedListedThings,
      Map<String, Resolved> deprecatedThings,
      List<Resolved> sortedUniqueResolved) {
    Map<String, Resolved> localArtifacts = new HashMap<>(importedArtifacts);
    for (Resolved localResolved : lockedFromLocal.values()) {
      Resolved importedResolved = localArtifacts.get(localResolved.key());
      if (!isNewerVersion(importedResolved, localResolved)) {
        localArtifacts.put(localResolved.key(), localResolved);
      }
    }

    List<Resolved> sortedUniqueLocked = sortResolvedThings(localArtifacts.values().stream()
        .filter(r -> !blackedListedThings.containsKey(r.key()))
        .filter(r -> !deprecatedThings.containsKey(r.key()))
        .filter(r -> !resolvedThings.containsKey(r.key()))
        .collect(toList()));

    warnAboutResolutionConflicts(sortedUniqueResolved);
    return sortedUniqueLocked;
  }

  private Predicate<Resolve> isLocallyDeclared(
      Map<String, Resolve> resolvesFromLocal, Map<String, Resolve> overridesFromLocal) {
    return resolve -> resolvesFromLocal.containsKey(resolve.key()) || overridesFromLocal.containsKey(resolve.key());
  }

  private void resolveArtifacts(RepositorySystemSession session, List<Resolve> toResolve) {
    List<MetadataRequest> metadataRequests = toResolve.map(this::buildMetaDataRequest);

    repositorySystem.resolveMetadata(session, metadataRequests.toJavaList());
  }

  private void checkForDuplicateResolutions(
      List<Resolve> imported, Map<String, Resolve> resolvesFromLocal, Map<String, Resolve>... resolutions) {
    for (Map<String, Resolve> resolveMap : resolutions) {
      // Check for duplicate resolves
      List<Resolve> importedMinusLocal =
          imported.filter(r -> resolvesFromLocal.keySet().contains(r.key()));

      List<String> duplicateResolutions = importedMinusLocal
          .filter(r -> resolveMap.keySet().contains(r.key()))
          .map(Resolve::key);

      if (!duplicateResolutions.isEmpty()) {
        throw new IllegalStateException(String.format(
            "pom.deps contains duplicate resolution declarations for: %s",
            duplicateResolutions.mkString(", ")));
      }
    }
  }

  private Predicate<Resolve> matchesAllowOrOverride(RangeResolver rangeResolver) {
    Predicate<String> predicate = matchesAny(allowPatterns(rangeResolver))
        .and(matchesAny(withheldPatterns(rangeResolver)).negate());

    List<String> overrideKeys = rangeResolver
        .resolves()
        .filter(res -> res.resolveType() == ResolveType.OVERRIDE)
        .map(Resolve::key);
    return resolve -> overrideKeys.contains(resolve.key()) || predicate.test(resolve.key());
  }

  private List<RangeResolver> parseImports(
      RangeResolver rangeResolver,
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories) {
    return rangeResolver.imports().map(i -> parseImport(sourceReference, session, repositories, i));
  }

  private List<Resolved> streamResolvedArtifacts(RangeResolver resolver) {
    return resolver.locked()
        .appendAll(resolver.resolved())
        .appendAll(resolver.deprecated())
        .appendAll(resolver.blacklisted());
  }

  public static List<Resolved> sortResolvedThings(Iterable<Resolved> values) {
    return List.ofAll(values).sortBy(Resolved::key);
  }

  private List<RemoteRepository> buildRepositories(Iterable<Repository> repositories) {
    return StreamSupport.stream(repositories.spliterator(), false)
        .map(repo -> buildRepository(repo, updateSnapshots))
        .collect(List.collector());
  }

  /**
   * Group a list of resolved artifacts by their groupId:artifactId, warn if multiple entries are found.
   *
   * @param resolved A list of {@link Resolved} items.
   */
  private void warnAboutResolutionConflicts(List<Resolved> resolved) {
    Map<String, java.util.List<Resolved>> conflicting = resolved.collect(Collectors.groupingBy(Resolved::key));
    for (Map.Entry<String, java.util.List<Resolved>> entry : conflicting.entrySet()) {
      if (entry.getValue().size() > 1) {
        String resolutionGavs = entry.getValue().stream()
            .map(r -> r.resolvedFrom().getOrElse(""))
            .collect(Collectors.joining(", "));
        warningLogger.accept(
            String.format("Duplicate resolution of %s found in: %s", entry.getKey(), resolutionGavs));
      }
    }
  }

  public static Set<Pattern> allowPatterns(RangeResolver rangeResolver) {
    TreeSet<Pattern> emptyPattens = TreeSet.of(Comparator.comparing(Pattern::toString));
    return rangeResolver.allows().foldLeft(emptyPattens, (patterns, allow) -> patterns.add(allow.pattern()));
  }

  public static Set<Pattern> withheldPatterns(RangeResolver rangeResolver) {
    TreeSet<Pattern> emptyPattens = TreeSet.of(Comparator.comparing(Pattern::toString));
    return rangeResolver
        .withheld()
        .foldLeft(emptyPattens, (patterns, withheld) -> patterns.add(withheld.pattern()));
  }

  private RangeResolver parseImport(
      String sourceReference, RepositorySystemSession session, List<RemoteRepository> repositories, Import i) {
    boolean requestSnapshots = i.version().endsWith("-SNAPSHOT");
    Bound importBound = requestSnapshots ? Bound.SNAPSHOT : Bound.HIGHEST;
    Pattern importFilter = requestSnapshots ? ALLOW_SNAPSHOTS : FILTER_SNAPSHOTS;
    Resolve imported = Resolve.of(
        "",
        i.group(),
        i.artifact(),
        BILL_OF_MATERIALS,
        DEPS,
        importBound,
        Range.fixed(i.version()),
        ResolveType.IMPORT,
        importFilter,
        sourceReference,
        "imported");

    // TODO Make resolveArtifact accept both Resolve and Imported
    try {
      resolveArtifact(new HashMap<>(), sourceReference, session, repositories, imported);
    } catch (IllegalStateException ignored) {
      // ignore
    }

    Artifact artifact = new DefaultArtifact(i.group(), i.artifact(), BILL_OF_MATERIALS, DEPS, i.version());
    File artifactFile;

    LocalArtifactRequest lar = new LocalArtifactRequest(artifact, repositories.toJavaList(), "");
    LocalArtifactResult localArtifactResult =
        session.getLocalRepositoryManager().find(session, lar);
    artifactFile = localArtifactResult.getFile();
    if (artifactFile == null) {
      throw new IllegalArgumentException(
          "Unable to resolve " + artifact + " in either local or remote repositories.");
    }

    try {
      RangeResolver resolver = parseRangeResolver(
          i.gav(), Files.newBufferedReader(artifactFile.toPath()), allowUnlocked, allowSnapshots);
      return snapshotDependencies(resolver, warningLogger, i.snapshots());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static RangeResolver unlockDependencies(RangeResolver resolver, Set<Pattern> unlocked) {
    Predicate<Resolved> isMatchingUnlocked =
        resolved -> matchesAny(unlocked).test(resolved.key());
    List<Resolved> allResolved = List();

    allResolved = allResolved.appendAll(resolver.locked().filter(isMatchingUnlocked));

    List<Resolved> locked = resolver.locked().filter(isMatchingUnlocked.negate());

    allResolved = allResolved.appendAll(resolver.resolved());

    return resolver.withResolved(sortResolvedThings(allResolved.toJavaList()))
        .withLocked(sortResolvedThings(locked.toJavaList()));
  }

  public static RangeResolver snapshotDependencies(
      RangeResolver importedRangeResolver, Consumer<String> warningLogger, Set<Pattern> snapshots) {
    Predicate<Resolve> isMatchingSnapshot = resolve -> matchesAny(snapshots).test(resolve.key());

    List<Resolve> snapshotResolves = importedRangeResolver.resolves().map(r -> {
      if (isMatchingSnapshot.test(r)) {
        warningLogger.accept("Allowing SNAPSHOT resolution of " + r.key());
        return r.withBounds(Bound.SNAPSHOT).withFiltering(ALLOW_SNAPSHOTS);
      } else {
        return r;
      }
    });

    importedRangeResolver =
        importedRangeResolver.withResolves(snapshotResolves).withLocked(importedRangeResolver.locked());
    return importedRangeResolver;
  }

  private static Predicate<String> matchesAny(Set<Pattern> patterns) {
    return value -> patterns.exists(pattern -> pattern.matcher(value).matches());
  }

  private Option<Resolved> resolveArtifact(
      Map<String, Resolved> lockedFromLocal,
      String sourceReference,
      RepositorySystemSession session,
      List<RemoteRepository> repositories,
      Resolve resolve) {
    try {
      Option<Range> resolvedVersionRange = resolveVersionRange(session, repositories, resolve);

      if (!resolvedVersionRange.isDefined()) {
        String repositorySource =
            repositories.map(RemoteRepository::toString).mkString("[", "|", "]");

        String baseError = String.format(
            "Unable to resolve %s releases in the range %s from %s, declared in %s",
            resolve.key(), resolve.range().asVersionRange(), repositorySource, sourceReference);

        warningLogger.accept(baseError);
        return none();

      } else {
        boolean deprecated = resolve.resolveType() == ResolveType.DEPRECATE;
        boolean blacklisted = resolve.resolveType() == ResolveType.BLACKLIST;

        Range constrainedRange = resolvedVersionRange.get();
        String boundVersion =
            resolve.bounds() == Bound.LOWEST ? constrainedRange.lower() : constrainedRange.upper();

        ResolvedType resolvedType = getResolvedType(resolve);

        // Has resolved version actually changed?
        Resolved previouslyLockedResolution = lockedFromLocal.get(resolve.key());

        if (previouslyLockedResolution == null
            || deprecated
            || blacklisted
            || isNewerVersion(boundVersion, previouslyLockedResolution.version())) {
          String existingVersion =
              previouslyLockedResolution == null ? "[undefined]" : previouslyLockedResolution.version();

          if (previouslyLockedResolution != null
              && isNewerVersion(boundVersion, previouslyLockedResolution.version())) {
            infoLogger.accept(String.format(
                "%s %s: %s is newer than %s",
                capitalize(resolvedType.name().toLowerCase()),
                resolve.key(),
                boundVersion,
                existingVersion));
          }

          Resolved newlyResolved = Resolved.of(
              "",
              MAVEN,
              resolve.group(),
              resolve.artifact(),
              boundVersion,
              some(sourceReference),
              resolve.propertyName(),
              constrainedRange,
              resolvedType);

          return some(newlyResolved);
        } else {
          return none();
        }
      }

    } catch (RepositoryException e) {
      warningLogger.accept(e.getMessage());
      return none();
    }
  }

  private ResolvedType getResolvedType(Resolve resolve) {
    return switch (resolve.resolveType()) {
      case DEPRECATE -> DEPRECATED;
      case BLACKLIST -> BLACKLISTED;
      case RESOLVE -> RESOLVED;
      default -> LOCKED;
    };
  }

  private Option<Range> resolveVersionRange(
      RepositorySystemSession session, List<RemoteRepository> repositories, Resolve resolve)
      throws VersionRangeResolutionException, ArtifactResolutionException {
    Artifact artifact = resolveToArtifact(resolve);
    VersionRangeRequest rangeRequest = new VersionRangeRequest();
    rangeRequest.setArtifact(artifact);
    rangeRequest.setRepositories(repositories.toJavaList());

    List<Version> resolvedVersions = List.ofAll(repositorySystem
            .resolveVersionRange(session, rangeRequest)
            .getVersions())
        .filter(filterVersion(resolve));

    if (resolvedVersions.isEmpty()) {
      return none();
    } else {
      Range resolvedRange = resolveBoundRange(resolvedVersions);
      Set<ArtifactRequest> resolvedArtifactRequests =
          resovleArtifactRequests(repositories, resolve, resolvedRange);
      repositorySystem.resolveArtifacts(session, resolvedArtifactRequests.toJavaSet());
      return Option.of(resolvedRange);
    }
  }

  private Set<ArtifactRequest> resovleArtifactRequests(
      List<RemoteRepository> repositories, Resolve resolve, Range range) {
    Artifact lowerArtifact = new DefaultArtifact(
        resolve.group(), resolve.artifact(), resolve.classifier(), resolve.extension(), range.lower());
    Artifact upperAritifact = new DefaultArtifact(
        resolve.group(), resolve.artifact(), resolve.classifier(), resolve.extension(), range.upper());

    return HashSet.of(
        new ArtifactRequest(lowerArtifact, repositories.toJavaList(), ""),
        new ArtifactRequest(upperAritifact, repositories.toJavaList(), ""));
  }

  private Range resolveBoundRange(List<Version> versions) {
    String lower = versions.get(0).toString();
    if (versions.size() == 1) {
      return Range.fixed(lower);
    } else {
      return Range.excludingUpper(lower, versions.get(versions.size() - 1).toString());
    }
  }

  public static boolean isNewerVersion(@Nullable String first, @Nullable String second) {
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

  public static boolean isNewerVersion(@Nullable Resolved first, @Nullable Resolved second) {
    if (second == null && first != null) {
      return true;
    }
    if (first == null) {
      return false;
    }

    return isNewerVersion(first.version(), second != null ? second.version() : null);
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
    return new MetadataRequest(new DefaultMetadata(rep.group(), rep.artifact(), Metadata.Nature.RELEASE));
  }

  private RemoteRepository buildRepository(AbstractRepository repo, boolean updateSnapshots) {
    String updatePolicy =
        updateSnapshots ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_DAILY;

    RemoteRepository.Builder builder = new RemoteRepository.Builder(repo.url(), "default", repo.url())
        .setPolicy(new RepositoryPolicy(true, updatePolicy, RepositoryPolicy.CHECKSUM_POLICY_WARN));

    return builder.build();
  }

  public static String capitalize(String word) {
    return word == null || word.trim().isEmpty() ? "" : word.substring(0, 1).toUpperCase() + word.substring(1);
  }
}
