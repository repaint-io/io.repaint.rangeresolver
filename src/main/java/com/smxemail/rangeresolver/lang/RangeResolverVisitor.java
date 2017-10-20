package com.smxemail.rangeresolver.lang;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.smxemail.rangeresolver.RangeResolverBaseVisitor;
import com.smxemail.rangeresolver.RangeResolverParser;
import com.smxemail.rangeresolver.api.Alias;
import com.smxemail.rangeresolver.api.Allow;
import com.smxemail.rangeresolver.api.Allowable;
import com.smxemail.rangeresolver.api.Bound;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.PropertyBased;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import io.vavr.collection.List;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class RangeResolverVisitor extends RangeResolverBaseVisitor<RangeResolver> {

  public static final String ARTIFACT_VERSION = "%s.version";

  public static final Pattern ALLOW_SNAPSHOTS = Pattern.compile("");

  public static final Pattern FILTER_SNAPSHOTS = Pattern.compile("^.*-SNAPSHOT$");

  private final String sourceReference;

  private final String allowUnlocked;

  private final String allowSnapshots;

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

    List<Allow> allowables = mapList(ctx.allow(), this::resolveAllow);

    if (!"".equals(allowUnlocked)) {
      allowables =
          allowables.append(Allow.of("", Allowable.UNLOCKED, Pattern.compile(allowUnlocked)));
    }
    if (!"".equals(allowSnapshots)) {
      allowables =
          allowables.append(Allow.of("", Allowable.SNAPSHOTS, Pattern.compile(allowSnapshots)));
    }

    List<Repository> repositories = mapList(ctx.repository(), this::resolveRepository);
    List<Import> imports = mapList(ctx.imported(), resolveImport(allowables));
    List<Resolve> overrides = mapList(ctx.override(), this::resolveOverride);
    List<Resolve> resolves = mapList(ctx.resolve(), this::resolveResolve);
    List<Alias> aliases = mapList(ctx.alias(), this::resolveAlias);

    Multimap<String, Repository> repositoryMap =
        Multimaps.index(repositories, Repository::grouping);

    if (hasDuplicates(imports)) {
      throw new IllegalStateException("Duplicate import declaration found in " + sourceReference);
    }
    if (hasDuplicates(aliases)) {
      throw new IllegalStateException("Duplicate alias declaration found in " + sourceReference);
    }

    Collector<PropertyBased, ?, java.util.Map<String, String>> collector =
        Collectors.toMap(PropertyBased::key, PropertyBased::propertyName);

    Map<String, String> propertyNames = resolves.collect(collector);
    Map<String, String> aliasNames = aliases.collect(collector);

    Map<String, String> effectivePropertyNames = new HashMap<>();
    effectivePropertyNames.putAll(propertyNames);
    effectivePropertyNames.putAll(aliasNames);

    List<Resolved> resolveds =
        mapList(ctx.resolved(), res -> resolveResolved(effectivePropertyNames, res));
    List<Resolved> locked =
        mapList(ctx.locked(), res -> resolveLocked(effectivePropertyNames, res));

    return RangeResolver.of(
        repositoryMap, imports, allowables, resolves, overrides, aliases, resolveds, locked);
  }

  private Allow resolveAllow(RangeResolverParser.AllowContext rep) {
    Pattern pattern = compileRegexLiteral(rep.REGEX_VALUE().getText());
    Allowable allowable = Allowable.valueOf(rep.ALLOWABLE().getText().toUpperCase());
    return Allow.of(comments(rep.comment()), allowable, pattern);
  }

  private boolean hasDuplicates(List<?> list) {
    return list.size() != list.toSet().size();
  }

  private Repository resolveRepository(RangeResolverParser.RepositoryContext rep) {

    String grouping = "default";
    if (rep.respositoryGrouping() != null) {
      grouping = rep.respositoryGrouping().value().getText();
    }

    return Repository.of(comments(rep.comment()), rep.href().getText(), grouping);
  }

  private Resolved resolveLocked(
      Map<String, String> propertyNames, RangeResolverParser.LockedContext res) {
    String group = res.resolvedArtifact().group().getText();
    String artifact = res.resolvedArtifact().artifactId().getText();
    String key = String.format("%s:%s", group, artifact);
    String version = res.resolvedArtifact().version().getText();
    return Resolved.of(
        comments(res.comment()),
        group,
        artifact,
        version,
        res.resolvedFromArtifact().getText(),
        propertyNames.getOrDefault(key, String.format(ARTIFACT_VERSION, artifact)),
        Range.fixed(version));
  }

  private Resolved resolveResolved(
      Map<String, String> propertyNames, RangeResolverParser.ResolvedContext res) {
    String group = res.resolvedArtifact().group().getText();
    String artifact = res.resolvedArtifact().artifactId().getText();
    String key =
        String.format(
            "%s:%s",
            res.resolvedArtifact().group().getText(),
            res.resolvedArtifact().artifactId().getText());
    String version = res.resolvedArtifact().version().getText();
    return Resolved.of(
        comments(res.comment()),
        group,
        artifact,
        version,
        res.resolvedFromArtifact().getText(),
        propertyNames.getOrDefault(key, String.format(ARTIFACT_VERSION, artifact)),
        Range.fixed(version));
  }

  private Function<RangeResolverParser.ImportedContext, Import> resolveImport(List<Allow> allows) {
    List<Pattern> unlocked =
        allows.filter(allow -> allow.allowable() == Allowable.UNLOCKED).map(Allow::pattern);

    List<Pattern> snapshots =
        allows.filter(allow -> allow.allowable() == Allowable.SNAPSHOTS).map(Allow::pattern);

    return importContext ->
        Import.of(
            comments(importContext.comment()),
            importContext.resolvedArtifact().group().getText(),
            importContext.resolvedArtifact().artifactId().getText(),
            importContext.resolvedArtifact().version().getText(),
            snapshots,
            unlocked);
  }

  private Resolve resolveResolve(RangeResolverParser.ResolveContext resolve) {
    return resolveResolution(resolve.resolution(), resolve.comment());
  }

  private Resolve resolveOverride(RangeResolverParser.OverrideContext overide) {
    return resolveResolution(overide.resolution(), overide.comment());
  }

  private Resolve resolveResolution(
      RangeResolverParser.ResolutionContext res,
      java.util.List<RangeResolverParser.CommentContext> comments) {
    RangeResolverParser.FilteringContext filtering = res.filtering();
    Bound bound = Bound.valueOf(res.bounds().getText().toUpperCase());
    Pattern filteringRegex = resolveFilteringPattern(filtering, bound);

    String grouping = "default";
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
        filteringRegex,
        sourceReference,
        grouping);
  }

  private Pattern resolveFilteringPattern(
      RangeResolverParser.FilteringContext filtering, Bound bound) {
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
      return Range.of(
          context.boundedRange().lowerBound().getText(),
          context.boundedRange().lowerBoundValue().getText(),
          context.boundedRange().upperBoundValue().getText(),
          context.boundedRange().upperBound().getText());
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
