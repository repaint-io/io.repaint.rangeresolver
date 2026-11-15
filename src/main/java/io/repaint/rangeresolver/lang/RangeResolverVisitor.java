package io.repaint.rangeresolver.lang;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import dev.zazr.collection.List;
import dev.zazr.collection.Set;
import dev.zazr.collection.TreeSet;

import io.repaint.rangeresolver.RangeResolverBaseVisitor;
import io.repaint.rangeresolver.RangeResolverParser;
import io.repaint.rangeresolver.api.Alias;
import io.repaint.rangeresolver.api.Allow;
import io.repaint.rangeresolver.api.Allowable;
import io.repaint.rangeresolver.api.ArtifactType;
import io.repaint.rangeresolver.api.Bound;
import io.repaint.rangeresolver.api.Import;
import io.repaint.rangeresolver.api.PropertyBased;
import io.repaint.rangeresolver.api.Range;
import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Repository;
import io.repaint.rangeresolver.api.Resolve;
import io.repaint.rangeresolver.api.ResolveType;
import io.repaint.rangeresolver.api.Resolved;
import io.repaint.rangeresolver.api.ResolvedType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.zazr.control.Option.none;

import static io.repaint.rangeresolver.api.Allowable.SNAPSHOTS;
import static io.repaint.rangeresolver.api.Bound.HIGHEST;
import static io.repaint.rangeresolver.api.ResolveType.BLACKLIST;
import static io.repaint.rangeresolver.api.ResolveType.DEPRECATE;
import static io.repaint.rangeresolver.api.ResolveType.OVERRIDE;
import static io.repaint.rangeresolver.api.ResolveType.RESOLVE;
import static io.repaint.rangeresolver.api.ResolvedType.BLACKLISTED;
import static io.repaint.rangeresolver.api.ResolvedType.DEPRECATED;
import static io.repaint.rangeresolver.api.ResolvedType.LOCKED;
import static io.repaint.rangeresolver.api.ResolvedType.RESOLVED;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/// Builds a [RangeResolver] model from a parsed range resolver script.
public class RangeResolverVisitor extends RangeResolverBaseVisitor<RangeResolver> {
    /// The format of the property name a resolved version is published under, given
    /// the artifact id.
    public static final String ARTIFACT_VERSION = "%s.version";

    /// A version filter that excludes nothing, letting SNAPSHOT versions through.
    public static final Pattern ALLOW_SNAPSHOTS = Pattern.compile("");

    /// The default version filter, excluding SNAPSHOT versions.
    public static final Pattern FILTER_SNAPSHOTS = Pattern.compile("^.*-SNAPSHOT$");

    /// The grouping used by repositories and resolutions that do not name one.
    public static final String DEFAULT_GROUP = "default";

    /// The grouping used for Docker images.
    public static final String DOCKER_GROUP = "docker";

    private final String sourceReference;

    private final String allowUnlocked;

    private final String allowSnapshots;

    /// Creates a visitor for one script.
    ///
    /// @param sourceReference the location the script was read from, recorded
    ///                        against each resolution request.
    /// @param allowUnlocked   a pattern of artifact keys to unlock, or an empty
    ///                        string for none.
    /// @param allowSnapshots  a pattern of artifact keys allowed to resolve to
    ///                        SNAPSHOT versions, or an empty string
    ///                        for none.
    public RangeResolverVisitor(String sourceReference, String allowUnlocked, String allowSnapshots) {
        this.sourceReference = sourceReference;
        this.allowUnlocked = allowUnlocked;
        this.allowSnapshots = allowSnapshots;
    }

    private <T, R> List<R> mapList(Iterable<T> list, Function<T, R> fn) {
        return StreamSupport.stream(list.spliterator(), false).map(fn).collect(List.collector());
    }

    @Override
    public RangeResolver visitResolver(RangeResolverParser.ResolverContext ctx) {
        Set<Allow> allowables = mapList(ctx.allow(), this::resolveAllow).toSortedSet();
        Set<Allow> withhelds = mapList(ctx.withhold(), this::resolveWithheld).toSortedSet();

        if (!"".equals(allowUnlocked)) {
            allowables = allowables.add(Allow.of("", Allowable.UNLOCKED, Pattern.compile(allowUnlocked), false));
        }
        if (!"".equals(allowSnapshots)) {
            allowables = allowables.add(Allow.of("", SNAPSHOTS, Pattern.compile(allowSnapshots), false));
        }

        List<Repository> repositories = mapList(ctx.repository(), this::resolveRepository);
        List<Import> imports = mapList(ctx.imported(), resolveImport(allowables, withhelds));
        List<Resolve> blacklists = mapList(ctx.blacklist(), this::resolveBlacklists);
        List<Resolve> deprecations = mapList(ctx.deprecate(), this::resolveDeprecations);
        List<Resolve> resolves = mapList(ctx.resolve(), this::resolveResolve);
        List<Resolve> overrides = mapList(ctx.override(), this::resolveOverride);
        List<Resolve> allResolves =
                blacklists.appendAll(deprecations).appendAll(resolves).appendAll(overrides);
        List<Alias> aliases = mapList(ctx.alias(), this::resolveAlias);

        Multimap<String, Repository> repositoryMap = Multimaps.index(repositories, Repository::grouping);

        if (hasDuplicates(imports)) {
            throw new IllegalStateException("Duplicate import declaration found in " + sourceReference);
        }
        if (hasDuplicates(aliases)) {
            throw new IllegalStateException("Duplicate alias declaration found in " + sourceReference);
        }

        Collector<PropertyBased, ?, Map<String, String>> collector =
                Collectors.toMap(PropertyBased::key, PropertyBased::propertyName);

        Map<String, String> propertyNames = resolves.collect(collector);
        Map<String, String> aliasNames = aliases.collect(collector);

        Map<String, String> effectivePropertyNames = new HashMap<>();
        effectivePropertyNames.putAll(propertyNames);
        effectivePropertyNames.putAll(aliasNames);

        List<Resolved> resolveds = mapList(ctx.resolved(), res -> resolveResolved(effectivePropertyNames, res));
        List<Resolved> locked = mapList(ctx.locked(), res -> resolveLocked(effectivePropertyNames, res));
        List<Resolved> deprecateds = mapList(ctx.deprecated(), res -> resolveDeprecated(effectivePropertyNames, res));
        List<Resolved> blacklisted = mapList(ctx.blacklisted(), res -> resolveBlacklisted(effectivePropertyNames, res));

        return RangeResolver.of(
                repositoryMap,
                imports,
                allowables,
                withhelds,
                allResolves,
                aliases,
                resolveds,
                deprecateds,
                blacklisted,
                locked);
    }

    private Allow resolveAllow(RangeResolverParser.AllowContext rep) {
        Pattern pattern = compileRegexLiteral(rep.REGEX_VALUE().getText());
        Allowable allowable = Allowable.valueOf(rep.ALLOWABLE().getText().toUpperCase(Locale.ROOT));
        return Allow.of(comments(rep.comment()), allowable, pattern, true);
    }

    private Allow resolveWithheld(RangeResolverParser.WithholdContext rep) {
        Pattern pattern = compileRegexLiteral(rep.REGEX_VALUE().getText());
        return Allow.of(comments(rep.comment()), SNAPSHOTS, pattern, true);
    }

    private boolean hasDuplicates(List<?> list) {
        return list.size() != list.toSet().size();
    }

    private Repository resolveRepository(RangeResolverParser.RepositoryContext rep) {
        String grouping = DEFAULT_GROUP;
        int cooldown = 0;

        if (rep.repositoryGrouping() != null) {
            grouping = rep.repositoryGrouping().value().getText();
        }
        if (rep.cooldown() != null) {
            cooldown = Integer.parseInt(rep.cooldown().value().getText());
        }

        return Repository.of(comments(rep.comment()), rep.href().getText(), grouping, cooldown);
    }

    record ArtifactInfo(ArtifactType type, String group, String artifact, String version) {}

    private ArtifactInfo liftArtifact(RangeResolverParser.ResolvedArtifactContext artifactContext) {
        var dockerImage = artifactContext.resolvedDockerImage();
        var mavenArtifact = artifactContext.resolvedMavenArtifact();

        if (dockerImage != null) {
            var artifactId = dockerImage.artifactId().getText();
            var version = dockerImage.digest().getText();

            return new ArtifactInfo(ArtifactType.DOCKER, DOCKER_GROUP, artifactId, version);
        } else {
            var group = mavenArtifact.group().getText();
            var artifactId = mavenArtifact.artifactId().getText();
            var version = mavenArtifact.version().getText();

            return new ArtifactInfo(ArtifactType.MAVEN, group, artifactId, version);
        }
    }

    private Resolved resolveArtifact(
            ResolvedType type,
            Map<String, String> propertyNames,
            ArtifactInfo artifactInfo,
            java.util.List<RangeResolverParser.CommentContext> comments) {
        String key = "%s:%s".formatted(artifactInfo.group(), artifactInfo.artifact());
        String version = artifactInfo.version();
        return Resolved.of(
                comments(comments),
                artifactInfo.type(),
                artifactInfo.group(),
                artifactInfo.artifact(),
                version,
                none(),
                propertyNames.getOrDefault(key, ARTIFACT_VERSION.formatted(artifactInfo.artifact())),
                Range.fixed(version),
                type);
    }

    private Resolved resolveLocked(Map<String, String> propertyNames, RangeResolverParser.LockedContext res) {
        return resolveArtifact(LOCKED, propertyNames, liftArtifact(res.resolvedArtifact()), res.comment());
    }

    private Resolved resolveDeprecated(Map<String, String> propertyNames, RangeResolverParser.DeprecatedContext res) {
        return resolveArtifact(DEPRECATED, propertyNames, liftArtifact(res.resolvedArtifact()), res.comment());
    }

    private Resolved resolveBlacklisted(Map<String, String> propertyNames, RangeResolverParser.BlacklistedContext res) {
        return resolveArtifact(BLACKLISTED, propertyNames, liftArtifact(res.resolvedArtifact()), res.comment());
    }

    private Resolved resolveResolved(Map<String, String> propertyNames, RangeResolverParser.ResolvedContext res) {
        return resolveArtifact(RESOLVED, propertyNames, liftArtifact(res.resolvedArtifact()), res.comment());
    }

    private Function<RangeResolverParser.ImportedContext, Import> resolveImport(
            Set<Allow> allows, Set<Allow> withhelds) {
        TreeSet<Pattern> emptyPattens = TreeSet.of(Comparator.comparing(Pattern::toString));

        Set<Pattern> unlocked = allows.filter(allow -> allow.allowable() == Allowable.UNLOCKED)
                .foldLeft(emptyPattens, (patterns, allow) -> patterns.add(allow.pattern()));

        Set<Pattern> snapshots = allows.filter(allow -> allow.allowable() == SNAPSHOTS)
                .foldLeft(emptyPattens, (patterns, allow) -> patterns.add(allow.pattern()));

        Set<Pattern> withheld = withhelds
                .filter(allow -> allow.allowable() == Allowable.UNLOCKED)
                .foldLeft(emptyPattens, (patterns, allow) -> patterns.add(allow.pattern()));

        return importContext -> {
            var artifact = liftArtifact(importContext.resolvedArtifact());
            return Import.of(
                    comments(importContext.comment()),
                    artifact.group(),
                    artifact.artifact(),
                    artifact.version(),
                    snapshots,
                    unlocked,
                    withheld);
        };
    }

    private Resolve resolveResolve(RangeResolverParser.ResolveContext resolve) {
        if (resolve.docker() != null) {
            return resolveDocker(resolve.docker());
        } else {
            return resolveResolution(resolve.resolution(), RESOLVE, resolve.comment());
        }
    }

    private Resolve resolveBlacklists(RangeResolverParser.BlacklistContext resolve) {
        return resolveResolution(resolve.resolution(), BLACKLIST, resolve.comment());
    }

    private Resolve resolveDeprecations(RangeResolverParser.DeprecateContext resolve) {
        return resolveResolution(resolve.resolution(), DEPRECATE, resolve.comment());
    }

    private Resolve resolveOverride(RangeResolverParser.OverrideContext overide) {
        return resolveResolution(overide.resolution(), OVERRIDE, overide.comment());
    }

    private Resolve resolveDocker(RangeResolverParser.DockerContext docker) {
        Range range = Range.fixed(docker.dockerImage().version().getText());
        return Resolve.of(
                comments(docker.comment()),
                DOCKER_GROUP,
                docker.dockerImage().getText(),
                "",
                "",
                HIGHEST,
                range,
                ResolveType.DOCKER,
                ALLOW_SNAPSHOTS,
                "",
                DOCKER_GROUP);
    }

    private Resolve resolveResolution(
            RangeResolverParser.ResolutionContext res,
            ResolveType resolveType,
            java.util.List<RangeResolverParser.CommentContext> comments) {
        RangeResolverParser.FilteringContext filtering = res.filtering();
        Bound bound = Bound.valueOf(res.bounds().getText().toUpperCase(Locale.ROOT));
        Pattern filteringRegex = resolveFilteringPattern(filtering, bound);

        String grouping = DEFAULT_GROUP;
        if (res.resolutionGrouping() != null) {
            grouping = res.resolutionGrouping().value().getText();
        }

        return Resolve.of(
                comments(comments),
                res.rangedArtifact().group().getText(),
                res.rangedArtifact().artifactId().getText(),
                "",
                "pom",
                bound,
                range(res.rangedArtifact().range()),
                resolveType,
                filteringRegex,
                sourceReference,
                grouping);
    }

    private Pattern resolveFilteringPattern(RangeResolverParser.FilteringContext filtering, Bound bound) {
        Pattern filteringRegex;

        if (filtering == null) {
            filteringRegex = bound == Bound.SNAPSHOT ? ALLOW_SNAPSHOTS : FILTER_SNAPSHOTS;
        } else {
            filteringRegex = compileRegexLiteral(filtering.filter().getText());
        }
        return filteringRegex;
    }

    private Pattern compileRegexLiteral(String regex) {
        return Pattern.compile(regex.substring(1, regex.length() - 1), CASE_INSENSITIVE);
    }

    private Alias resolveAlias(RangeResolverParser.AliasContext res) {
        return Alias.of(
                comments(res.comment()),
                res.artifact().group().getText(),
                res.artifact().artifactId().getText(),
                alias(res.aliasValue()));
    }

    private Range range(RangeResolverParser.RangeContext context) {
        if (context.fixedRange() != null) {
            return Range.fixed(context.fixedRange().value().getText());
        } else if (context.implicitRange() != null) {
            return Range.fixed(context.implicitRange().getText());
        } else {
            RangeResolverParser.BoundedRangeContext bounded = context.boundedRange();
            return Range.of(
                    bounded.lowerBound().getText(),
                    bounded.lowerBoundValue() != null
                            ? bounded.lowerBoundValue().getText()
                            : "",
                    bounded.upperBoundValue() != null
                            ? bounded.upperBoundValue().getText()
                            : "",
                    bounded.upperBound().getText());
        }
    }

    private String comments(Iterable<RangeResolverParser.CommentContext> comment) {
        if (comment == null) {
            return "";
        } else {
            return String.join("\n", mapList(comment, c -> c.getText().trim())).trim();
        }
    }

    private String alias(RangeResolverParser.AliasValueContext context) {
        return context != null ? context.value().getText() : "";
    }
}
