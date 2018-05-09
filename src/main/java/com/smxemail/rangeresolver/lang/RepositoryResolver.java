package com.smxemail.rangeresolver.lang;

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
import static com.smxemail.rangeresolver.lang.Memoizer.memo;
import static com.smxemail.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.ALLOW_SNAPSHOTS;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.FILTER_SNAPSHOTS;
import static io.vavr.API.List;
import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;
import static java.util.stream.Collectors.toList;

public class RepositoryResolver {

  public static final String BILL_OF_MATERIALS = "bill-of-materials";

  public static final String DEPS = "deps";

  private Consumer<String> infoLogger;

  private Consumer<String> warningLogger;

  private String localRepoDir;

  private String allowUnlocked;

  private String allowSnapshots;

  private boolean updateSnapshots;

  private RepositorySystem repositorySystem;

  private RepositoryResolver(
      Consumer<String> infoLogger,
      Consumer<String> warningLogger,
      String localRepoDir,
      String allowUnlocked,
      String allowSnapshots,
      boolean updateSnapshots) {
    this.infoLogger = infoLogger;
    this.warningLogger = warningLogger;
    this.localRepoDir = localRepoDir;
    this.allowUnlocked = allowUnlocked;
    this.allowSnapshots = allowSnapshots;
    this.updateSnapshots = updateSnapshots;
    this.repositorySystem = newRepositorySystem();
  }

  public static RepositoryResolver createRepositoryResolver(
      Consumer<String> infoLogger,
      Consumer<String> warningLogger,
      String localRepoDir,
      String allowUnlocked,
      String allowSnapshots,
      boolean updateSnapshots) {
    return new RepositoryResolver(
        infoLogger, warningLogger, localRepoDir, allowUnlocked, allowSnapshots, updateSnapshots);
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

    repositorySystem.newResolutionRepositories(session, repositories.toJavaList());

    List<RangeResolver> importedResolvers =
        parseImports(rangeResolver, sourceReference, session, repositories);

    Map<String, Resolved> importedArtifacts =
        importedResolvers.flatMap(this::streamResolvedArtifacts).collect(Resolved.collectByKey);

    Map<String, Resolve> resolvesFromLocal = rangeResolver.resolves().collect(Resolve.collectByKey);

    Map<String, Resolve> overridesFromLocal =
        rangeResolver.overrides().collect(Resolve.collectByKey);

    Map<String, Resolved> lockedFromLocal = rangeResolver.locked().collect(Resolved.collectByKey);

    List<Resolve> imported = importedResolvers.flatMap(RangeResolver::resolves);

    // Check for duplicate reolves
    List<String> duplicateResolutions =
        imported.filter(r -> resolvesFromLocal.keySet().contains(r.key())).map(Resolve::key);

    if (!duplicateResolutions.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "pom.deps contains duplicate resolve declarations for: %s",
              duplicateResolutions.mkString(", ")));
    }

    Predicate<Resolve> isUpdatedAllowed = matchesAllowOrOverride(rangeResolver);

    infoLogger.accept("Resolving metadata for " + resolverReference);
    List<Resolve> toResolve =
        imported
            .filter(r -> !overridesFromLocal.keySet().contains(r.key()))
            .filter(isUpdatedAllowed)
            .appendAll(rangeResolver.overrides())
            .appendAll(rangeResolver.resolves());

    List<MetadataRequest> metadataRequests = toResolve.map(this::buildMetaDataRequest);

    repositorySystem.resolveMetadata(session, metadataRequests.toJavaList());

    Function<Resolve, List<RemoteRepository>> buildRepositories =
        memo(resolve -> buildeRepositories(rangeResolver.repositories().get(resolve.grouping())));

    Function<Resolve, Option<Resolved>> resolveArtifact =
        memo(
            resolve ->
                resolveArtifact(
                    lockedFromLocal,
                    resolverReference,
                    session,
                    buildRepositories.apply(resolve),
                    resolve));

    Predicate<Resolve> isLocallyDeclared = resolve -> resolvesFromLocal.containsKey(resolve.key()) || overridesFromLocal.containsKey(resolve.key());
    Predicate<Resolve> isUpdatedVersion =
        resolve ->
            resolveArtifact
                .apply(resolve)
                .map(
                    resolved ->
                        isNewerVersion(resolved, importedArtifacts.get(resolve.key()))
                            && isNewerVersion(resolved, lockedFromLocal.get(resolve.key())))
                .getOrElse(false);

    Predicate<Resolve> isResolvable =
        resolve ->
            ((isUpdatedAllowed.test(resolve)) && isUpdatedVersion.test(resolve))
                || isLocallyDeclared.test(resolve);

    Map<String, Resolved> resolvedThings =
        toResolve.filter(isResolvable).flatMap(resolveArtifact).collect(Resolved.collectByKey);

    List<Resolved> sortedUniqueResolved = sortResolvedThings(resolvedThings.values());

    Map<String, Resolved> localArtifacts = new HashMap<>(importedArtifacts);
    for (Resolved localResolved : lockedFromLocal.values()) {
      Resolved importedResolved = localArtifacts.get(localResolved.key());
      if (!isNewerVersion(importedResolved, localResolved)) {
        localArtifacts.put(localResolved.key(), localResolved);
      }
    }

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

  private Predicate<Resolve> matchesAllowOrOverride(RangeResolver rangeResolver) {
    Predicate<String> predicate = matchesAny(allowPatterns(rangeResolver));
    List<String> overrideKeys = rangeResolver.overrides().map(Resolve::key);
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
    return resolver.resolved().appendAll(resolver.locked());
  }

  public static List<Resolved> sortResolvedThings(Iterable<Resolved> values) {
    return List.ofAll(values).sortBy(Resolved::key);
  }

  private List<RemoteRepository> buildeRepositories(Iterable<Repository> repositories) {
    return StreamSupport.stream(repositories.spliterator(), false)
        .map(repo -> buildRepository(repo, updateSnapshots))
        .collect(List.collector());
  }

  /**
   * Group a list of resolved artifacts by their groupId:artifactId, warn if multiple entries are
   * found.
   *
   * @param resolved A list of {@link Resolved} items.
   */
  private void warnAboutResolutionConflicts(List<Resolved> resolved) {
    Map<String, java.util.List<Resolved>> conflicting =
        resolved.collect(Collectors.groupingBy(Resolved::key));
    for (Map.Entry<String, java.util.List<Resolved>> entry : conflicting.entrySet()) {
      if (entry.getValue().size() > 1) {
        String resolutionGavs =
            entry.getValue().stream().map(Resolved::resolvedFrom).collect(Collectors.joining(", "));
        warningLogger.accept(
            String.format(
                "Duplicate resolution of %s found in: %s", entry.getKey(), resolutionGavs));
      }
    }
  }

  public static Set<Pattern> allowPatterns(RangeResolver rangeResolver) {
    TreeSet<Pattern> emptyPattens = TreeSet.of(Comparator.comparing(Pattern::toString));
    return rangeResolver
        .allows()
        .foldLeft(emptyPattens, (patterns, allow) -> patterns.add(allow.pattern()));
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
      resolveArtifact(new HashMap<>(), sourceReference, session, repositories, imported);
    } catch (IllegalStateException ignored) {
      // ignore
    }

    Artifact artifact =
        new DefaultArtifact(i.group(), i.artifact(), BILL_OF_MATERIALS, DEPS, i.version());
    File artifactFile;

    LocalArtifactRequest lar = new LocalArtifactRequest(artifact, repositories.toJavaList(), "");
    LocalArtifactResult localArtifactResult =
        session.getLocalRepositoryManager().find(session, lar);
    artifactFile = localArtifactResult.getFile();
    if (artifactFile == null) {
      throw new IllegalArgumentException(
          "Unable to resolve " + artifact.toString() + " in either local or remote repositories.");
    }

    try {
      RangeResolver resolver =
          parseRangeResolver(i.gav(), new FileReader(artifactFile), allowUnlocked, allowSnapshots);
      return snapshotDependencies(resolver, warningLogger, i.snapshots());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static RangeResolver unlockDependencies(RangeResolver resolver, Set<Pattern> unlocked) {
    Predicate<Resolved> isMatchingUnlocked = resolved -> matchesAny(unlocked).test(resolved.key());
    List<Resolved> allResolved = List();

    allResolved = allResolved.appendAll(resolver.locked().filter(isMatchingUnlocked));

    List<Resolved> locked = resolver.locked().filter(isMatchingUnlocked.negate());

    allResolved = allResolved.appendAll(resolver.resolved());

    return resolver
        .withResolved(sortResolvedThings(allResolved.toJavaList()))
        .withLocked(sortResolvedThings(locked.toJavaList()));
  }

  public static RangeResolver snapshotDependencies(
      RangeResolver importedRangeResolver, Consumer<String> warningLogger, Set<Pattern> snapshots) {
    Predicate<Resolve> isMatchingSnapshot = resolve -> matchesAny(snapshots).test(resolve.key());

    List<Resolve> snapshotResolves =
        importedRangeResolver
            .resolves()
            .map(
                r -> {
                  if (isMatchingSnapshot.test(r)) {
                    warningLogger.accept("Allowing SNAPSHOT resolution of " + r.key());
                    return r.withBounds(Bound.SNAPSHOT).withFiltering(ALLOW_SNAPSHOTS);
                  } else {
                    return r;
                  }
                });

    importedRangeResolver =
        importedRangeResolver
            .withResolves(snapshotResolves)
            .withLocked(importedRangeResolver.locked());
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

        String baseError =
            String.format(
                "Unable to resolve %s releases in the range %s from %s, declared in %s",
                resolve.key(), resolve.range().asVersionRange(), repositorySource, sourceReference);

        warningLogger.accept(baseError);
        return none();

      } else {
        Range constrainedRange = resolvedVersionRange.get();
        String boundVersion =
            resolve.bounds() == Bound.LOWEST ? constrainedRange.lower() : constrainedRange.upper();

        // Has resolved version actually changed?
        Resolved previouslyLockedResolution = lockedFromLocal.get(resolve.key());

        if (previouslyLockedResolution == null
            || isNewerVersion(boundVersion, previouslyLockedResolution.version())) {
          Resolved newlyResolved =
              Resolved.of(
                  "",
                  resolve.group(),
                  resolve.artifact(),
                  boundVersion,
                  sourceReference,
                  resolve.propertyName(),
                  constrainedRange);

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

  private Option<Range> resolveVersionRange(
      RepositorySystemSession session, List<RemoteRepository> repositories, Resolve resolve)
      throws VersionRangeResolutionException, ArtifactResolutionException {

    Artifact artifact = resolveToArtifact(resolve);
    VersionRangeRequest rangeRequest = new VersionRangeRequest();
    rangeRequest.setArtifact(artifact);
    rangeRequest.setRepositories(repositories.toJavaList());

    List<Version> resolvedVersions =
        List.ofAll(repositorySystem.resolveVersionRange(session, rangeRequest).getVersions())
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
