package io.repaint.rangeresolver.lang;

import dev.zazr.collection.HashSet;
import dev.zazr.collection.List;
import dev.zazr.collection.Set;
import dev.zazr.collection.TreeSet;
import dev.zazr.control.Option;

import io.repaint.rangeresolver.api.AbstractRepository;
import io.repaint.rangeresolver.api.AbstractResolve;
import io.repaint.rangeresolver.api.ArtifactType;
import io.repaint.rangeresolver.api.Bound;
import io.repaint.rangeresolver.api.Import;
import io.repaint.rangeresolver.api.Range;
import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Repository;
import io.repaint.rangeresolver.api.ResolutionResult;
import io.repaint.rangeresolver.api.Resolve;
import io.repaint.rangeresolver.api.ResolveType;
import io.repaint.rangeresolver.api.Resolved;
import io.repaint.rangeresolver.api.ResolvedType;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.repository.supplier.RepositorySystemSupplier;
import org.apache.maven.repository.supplier.SessionBuilderSupplier;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.version.Version;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.emptyToNull;

import static dev.zazr.control.Option.none;
import static dev.zazr.control.Option.some;

import static io.repaint.rangeresolver.api.ArtifactType.MAVEN;
import static io.repaint.rangeresolver.api.ResolvedType.BLACKLISTED;
import static io.repaint.rangeresolver.api.ResolvedType.DEPRECATED;
import static io.repaint.rangeresolver.api.ResolvedType.LOCKED;
import static io.repaint.rangeresolver.api.ResolvedType.RESOLVED;
import static io.repaint.rangeresolver.lang.Memoizer.memo;
import static io.repaint.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static io.repaint.rangeresolver.lang.RangeResolverVisitor.ALLOW_SNAPSHOTS;
import static io.repaint.rangeresolver.lang.RangeResolverVisitor.DEFAULT_GROUP;
import static io.repaint.rangeresolver.lang.RangeResolverVisitor.FILTER_SNAPSHOTS;

import static org.codehaus.plexus.util.CollectionUtils.mergeMaps;

import static java.util.stream.Collectors.toList;

/** Resolves the version ranges in a range resolver model against Maven repositories. */
public final class RepositoryResolver {
    /** The classifier of the artifact an {@code import} statement fetches. */
    public static final String BILL_OF_MATERIALS = "bill-of-materials";

    /** The extension of the artifact an {@code import} statement fetches. */
    public static final String DEPS = "deps";

    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;
    private final String allowUnlocked;
    private final String allowSnapshots;
    private final boolean updateSnapshots;
    private RepositorySystem repositorySystem;
    private final RepositorySystemSession session;

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

    /**
     * Creates a resolver with its own repository system, using the given local repository.
     *
     * @param infoLogger receives progress messages.
     * @param warningLogger receives warnings, such as duplicate resolutions.
     * @param localRepoDir the local repository directory.
     * @param allowUnlocked a pattern of artifact keys to unlock in imported scripts, or an empty string for none.
     * @param allowSnapshots a pattern of artifact keys in imported scripts allowed to resolve to SNAPSHOT versions, or
     *     an empty string for none.
     * @param updateSnapshots whether to check remote repositories for updates on every resolution rather than daily.
     * @return the resolver.
     */
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

    /**
     * Creates a resolver using an existing repository system and session, such as those of a running Maven build.
     *
     * @param repositorySystem the repository system to resolve with.
     * @param session the session, supplying the local repository and {@code settings.xml} mirrors, proxies and
     *     credentials.
     * @param infoLogger receives progress messages.
     * @param warningLogger receives warnings, such as duplicate resolutions.
     * @param allowUnlocked a pattern of artifact keys to unlock in imported scripts, or an empty string for none.
     * @param allowSnapshots a pattern of artifact keys in imported scripts allowed to resolve to SNAPSHOT versions, or
     *     an empty string for none.
     * @param updateSnapshots whether to check remote repositories for updates on every resolution rather than daily.
     * @return the resolver.
     */
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
        return new RepositorySystemSupplier().get();
    }

    private static RepositorySystemSession newRepositorySystemSession(
            RepositorySystem repositorySystem, String localRepoDir) {
        return new SessionBuilderSupplier(repositorySystem)
                .get()
                .withLocalRepositoryBaseDirectories(Path.of(localRepoDir))
                .build();
    }

    /**
     * Resolve artifact requests from the resolution model.
     *
     * @param rangeResolver The range resolution model.
     * @param resolverReference A reference to the resolver being processed, used in log and error messages.
     * @param sourceReference The location the resolver was loaded from, used to resolve relative imports.
     * @return A list of resolved artifacts
     */
    public ResolutionResult resolveRanges(
            RangeResolver rangeResolver, String resolverReference, String sourceReference) {
        List<RemoteRepository> repositories =
                buildRepositories(rangeResolver.repositories().values());

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
                        || (isNewerVersion(resolved, importedArtifacts.get(resolve.key()))
                                && isNewerVersion(resolved, resolvedFromLocalForVersionChecking.get(resolve.key()))))
                .getOrElse(false);

        Predicate<Resolve> isResolvable =
                isUpdatedAllowed.and(isUpdatedVersion).or(isLocallyDeclared).or(isLocallyDeclaredDocker);

        Map<String, Resolved> resolvedThings = toResolve
                .filter(isResolvable)
                .filter(r -> !deprecatedKeysFromLocal.contains(r.key()))
                .collect(resolveArtifact)
                .collect(Resolved.collectByKey);

        Map<String, Resolved> importedBlacklists = filterImported(importedArtifacts, BLACKLISTED);

        Map<String, Resolved> importedDeprecations = filterImported(importedArtifacts, DEPRECATED);

        Map<String, Resolved> localDeprecations =
                toResolve.filter(isLocallyDeprecated).collect(resolveArtifact).collect(Resolved.collectByKey);

        Map<String, Resolved> localBlacklists =
                toResolve.filter(isLocallyBlacklisted).collect(resolveArtifact).collect(Resolved.collectByKey);

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

            return resolved.flatMap(image -> {
                if (!image.contains("@")) {
                    warningLogger.accept("Unable to resolve image for %s".formatted(imageName));
                    return Option.none();
                } else {
                    var parts = image.split("@", 2);
                    var artifact = parts[0];
                    var version = parts[1];
                    var range = Range.of("", version, version, "");
                    return Option.some(Resolved.of(
                            "",
                            ArtifactType.DOCKER,
                            resolve.group(),
                            artifact,
                            version,
                            none(),
                            resolve.propertyName(),
                            range,
                            RESOLVED));
                }
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
        return rangeResolver
                .locked()
                .filter(r -> !overridenKeys.contains(r.key()))
                .collect(Resolved.collectByKey);
    }

    private Map<String, Resolved> collectAllResolvedVersions(RangeResolver rangeResolver) {
        return rangeResolver
                .blacklisted()
                .appendAll(rangeResolver.locked())
                .appendAll(rangeResolver.deprecated())
                .collect(Resolved.collectByKey);
    }

    private <K, V> Map<K, V> mergeMaps(Map<K, V> importedDeprecations, Map<K, V> localDeprecations) {
        Map<K, V> deprecatedThings = new HashMap<>();
        deprecatedThings.putAll(importedDeprecations);
        deprecatedThings.putAll(localDeprecations);
        return deprecatedThings;
    }

    private Map<String, Resolved> filterImported(Map<String, Resolved> importedArtifacts, ResolvedType blacklisted) {
        return dev.zazr.collection.HashMap.ofAll(importedArtifacts)
                .filterValues(res -> res.resolutionType() == blacklisted)
                .asJavaMap();
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

        repositorySystem.resolveMetadata(session, metadataRequests.asJava());
    }

    @SafeVarargs
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
                throw new IllegalStateException("pom.deps contains duplicate resolution declarations for: %s"
                        .formatted(duplicateResolutions.mkString(", ")));
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

    /**
     * Sorts resolved artifacts by their key.
     *
     * @param values the artifacts to sort.
     * @return the artifacts ordered by key.
     */
    public static List<Resolved> sortResolvedThings(Iterable<Resolved> values) {
        return List.ofAll(values).sortBy(Resolved::key);
    }

    /**
     * Build remote repositories, applying the session's mirror, proxy and authentication selectors so that credentials
     * from {@code settings.xml} are attached.
     */
    private List<RemoteRepository> buildRepositories(Iterable<Repository> repositories) {
        java.util.List<RemoteRepository> declared = List.ofAll(repositories)
                .map(repo -> buildRepository(repo, updateSnapshots))
                .asJava();
        return List.ofAll(repositorySystem.newResolutionRepositories(session, declared));
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
                        "Duplicate resolution of %s found in: %s".formatted(entry.getKey(), resolutionGavs));
            }
        }
    }

    /**
     * Collects the patterns of a model's {@code allow} rules.
     *
     * @param rangeResolver the model.
     * @return the allow patterns.
     */
    public static Set<Pattern> allowPatterns(RangeResolver rangeResolver) {
        TreeSet<Pattern> emptyPattens = TreeSet.of(Comparator.comparing(Pattern::toString));
        return rangeResolver.allows().foldLeft(emptyPattens, (patterns, allow) -> patterns.add(allow.pattern()));
    }

    /**
     * Collects the patterns of a model's {@code withhold} rules.
     *
     * @param rangeResolver the model.
     * @return the withhold patterns.
     */
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
        Path artifactPath;

        LocalArtifactRequest lar = new LocalArtifactRequest(artifact, repositories.asJava(), "");
        LocalArtifactResult localArtifactResult =
                session.getLocalRepositoryManager().find(session, lar);

        artifactPath = localArtifactResult.getPath();
        if (artifactPath == null) {
            throw new IllegalArgumentException(
                    "Unable to resolve " + artifact + " in either local or remote repositories.");
        }

        try {
            RangeResolver resolver =
                    parseRangeResolver(i.gav(), Files.newBufferedReader(artifactPath), allowUnlocked, allowSnapshots);
            return snapshotDependencies(resolver, warningLogger, i.snapshots());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Moves locked artifacts whose keys match any of the patterns into the resolved list, so they are re-resolved.
     *
     * @param resolver the model to unlock.
     * @param unlocked patterns of artifact keys to unlock.
     * @return the model with matching artifacts unlocked.
     */
    public static RangeResolver unlockDependencies(RangeResolver resolver, Set<Pattern> unlocked) {
        Predicate<Resolved> isMatchingUnlocked =
                resolved -> matchesAny(unlocked).test(resolved.key());
        List<Resolved> allResolved =
                resolver.locked().filter(isMatchingUnlocked).appendAll(resolver.resolved());

        List<Resolved> locked = resolver.locked().filter(isMatchingUnlocked.negate());

        return resolver.withResolved(sortResolvedThings(allResolved)).withLocked(sortResolvedThings(locked));
    }

    /**
     * Lets resolution requests whose keys match any of the patterns resolve to SNAPSHOT versions.
     *
     * @param importedRangeResolver the model to adjust.
     * @param warningLogger receives a warning for each request allowed to resolve a SNAPSHOT.
     * @param snapshots patterns of artifact keys to allow SNAPSHOTs for.
     * @return the model with matching requests switched to {@link Bound#SNAPSHOT}.
     */
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

                String baseError = "Unable to resolve %s releases in the range %s from %s, declared in %s"
                        .formatted(resolve.key(), resolve.range().asVersionRange(), repositorySource, sourceReference);

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
                        infoLogger.accept("%s %s: %s is newer than %s"
                                .formatted(
                                        capitalize(resolvedType.name().toLowerCase(Locale.ROOT)),
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
        rangeRequest.setRepositories(repositories.asJava());

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
            repositorySystem.resolveArtifacts(session, resolvedArtifactRequests.asJava());
            return some(resolvedRange);
        }
    }

    private Set<ArtifactRequest> resovleArtifactRequests(
            List<RemoteRepository> repositories, Resolve resolve, Range range) {
        Artifact lowerArtifact = new DefaultArtifact(
                resolve.group(), resolve.artifact(), resolve.classifier(), resolve.extension(), range.lower());
        Artifact upperAritifact = new DefaultArtifact(
                resolve.group(), resolve.artifact(), resolve.classifier(), resolve.extension(), range.upper());

        return HashSet.of(
                new ArtifactRequest(lowerArtifact, repositories.asJava(), ""),
                new ArtifactRequest(upperAritifact, repositories.asJava(), ""));
    }

    private Range resolveBoundRange(List<Version> versions) {
        String lower = versions.get(0).toString();
        if (versions.size() == 1) {
            return Range.fixed(lower);
        } else {
            return Range.excludingUpper(lower, versions.get(versions.size() - 1).toString());
        }
    }

    /**
     * Compares two version strings using Maven version ordering.
     *
     * @param first the version to test.
     * @param second the version to compare against.
     * @return {@code true} if {@code first} is newer than {@code second}, or if only {@code second} is {@code null}.
     */
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

    /**
     * Compares the versions of two resolved artifacts using Maven version ordering.
     *
     * @param first the artifact to test.
     * @param second the artifact to compare against.
     * @return {@code true} if {@code first} is newer than {@code second}, or if only {@code second} is {@code null}.
     */
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

    /**
     * A named repository ({@code repository <url> as <name>}) uses its name as its id, so it matches the
     * {@code <server>} and {@code <mirror>} entries in {@code settings.xml}. Unnamed repositories use their URL.
     */
    static String repositoryId(AbstractRepository repo) {
        return repo.grouping().equals(DEFAULT_GROUP) ? repo.url() : repo.grouping();
    }

    private RemoteRepository buildRepository(AbstractRepository repo, boolean updateSnapshots) {
        String updatePolicy =
                updateSnapshots ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_DAILY;

        RemoteRepository.Builder builder = new RemoteRepository.Builder(repositoryId(repo), "default", repo.url())
                .setPolicy(new RepositoryPolicy(true, updatePolicy, RepositoryPolicy.CHECKSUM_POLICY_WARN));

        return builder.build();
    }

    /**
     * Upper-cases the first character of a word.
     *
     * @param word the word, which may be {@code null}.
     * @return the capitalized word, or an empty string if the word is {@code null} or blank.
     */
    public static String capitalize(String word) {
        return word == null || word.trim().isEmpty()
                ? ""
                : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }
}
