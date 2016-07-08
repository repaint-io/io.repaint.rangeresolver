package com.smxemail.rangeresolver.lang;

import com.smxemail.rangeresolver.RangeResolverBaseVisitor;
import com.smxemail.rangeresolver.RangeResolverParser;
import com.smxemail.rangeresolver.api.Alias;
import com.smxemail.rangeresolver.api.Bound;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.PropertyBased;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class RangeResolverVisitor extends RangeResolverBaseVisitor<RangeResolver> {

  public static final String ARTIFACT_VERSION = "%s.version";

  private final String sourceReference;

  public RangeResolverVisitor(String sourceReference) {
    this.sourceReference = sourceReference;
  }

  private <T, R> List<R> mapList(List<T> list, Function<T, R> fn) {
    return list.stream().map(fn).collect(Collectors.toList());
  }

  @Override
  public RangeResolver visitResolver(RangeResolverParser.ResolverContext ctx) {

    List<Repository> repositories = mapList(ctx.repository(), this::resolveRepository);
    List<Import> imports = mapList(ctx.imported(), this::resolveImport);
    List<Resolve> resolves = mapList(ctx.resolve(), this::resolveResolve);
    List<Alias> aliases = mapList(ctx.alias(), this::resolveAlias);

    if (hasDuplicates(imports)) {
      throw new IllegalStateException("Duplicate import declaration found in " + sourceReference);
    }
    if (hasDuplicates(aliases)) {
      throw new IllegalStateException("Duplicate alias declaration found in " + sourceReference);
    }

    Collector<PropertyBased, ?, java.util.Map<String, String>> collector = Collectors.toMap(PropertyBased::key, PropertyBased::propertyName);

    Map<String, String> propertyNames = resolves.stream().collect(collector);
    Map<String, String> aliasNames = aliases.stream().collect(collector);

    Map<String, String> effectivePropertyNames = new HashMap<>();
    effectivePropertyNames.putAll(propertyNames);
    effectivePropertyNames.putAll(aliasNames);

    List<Resolved> resolveds = mapList(ctx.resolved(), (res) -> resolveResolved(effectivePropertyNames, res));
    List<Resolved> locked = mapList(ctx.locked(), (res) -> resolveLocked(effectivePropertyNames, res));

    return RangeResolver.of(repositories, imports, resolves, aliases, resolveds, locked);
  }

  private boolean hasDuplicates(List<?> list) {
    return list.size() != new HashSet(list).size();
  }

  private Repository resolveRepository(RangeResolverParser.RepositoryContext rep) {
    return Repository.of(comments(rep.comment()), rep.href().getText());
  }

  private Resolved resolveLocked(Map<String, String> propertyNames, RangeResolverParser.LockedContext res) {
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

  private Resolved resolveResolved(Map<String, String> propertyNames, RangeResolverParser.ResolvedContext res) {
    String group = res.resolvedArtifact().group().getText();
    String artifact = res.resolvedArtifact().artifactId().getText();
    String key = String.format("%s:%s", res.resolvedArtifact().group().getText(), res.resolvedArtifact().artifactId().getText());
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

  private Import resolveImport(RangeResolverParser.ImportedContext res) {
    return Import.of(
        comments(res.comment()),
        res.resolvedArtifact().group().getText(),
        res.resolvedArtifact().artifactId().getText(),
        res.resolvedArtifact().version().getText(),
        res.unlocked() != null);
  }

  private Resolve resolveResolve(RangeResolverParser.ResolveContext res) {

    Bound bound = Bound.valueOf(res.bounds().getText().toUpperCase());

    Optional<Pattern> filteringRegex = res.filtering() == null ? Optional.empty() : Optional.of(compileRegexLiteral(res.filtering().filter().getText()));

    if (bound == Bound.SNAPSHOT) {
      filteringRegex = Optional.of(Pattern.compile(""));
    }

    return Resolve.of(
        comments(res.comment()),
        res.rangedArtifact().group().getText(),
        res.rangedArtifact().artifactId().getText(),
        Bound.valueOf(res.bounds().getText().toUpperCase()),
        range(res.rangedArtifact().range()),
        filteringRegex);
  }

  private Pattern compileRegexLiteral(String regex) {
    return Pattern.compile(regex.substring(1, regex.length() - 1));
  }

  private Alias resolveAlias(RangeResolverParser.AliasContext res) {
    return Alias.of(comments(res.comment()), res.artifact().group().getText(), res.artifact().artifactId().getText(), alias(res.aliasValue()));
  }

  private Range range(RangeResolverParser.RangeContext context) {
    if (context.fixedRange() != null) {
      return Range.fixed(context.fixedRange().value().getText());
    } else {
      return Range.of(
          context.boundedRange().lowerBound().getText(),
          context.boundedRange().lowerBoundValue().getText(),
          context.boundedRange().upperBoundValue().getText(),
          context.boundedRange().upperBound().getText());
    }
  }

  private String comments(List<RangeResolverParser.CommentContext> comment) {
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
