package com.smxemail.rangeresolver.lang;

import com.smxemail.rangeresolver.RangeResolverBaseVisitor;
import com.smxemail.rangeresolver.RangeResolverParser;
import com.smxemail.rangeresolver.api.AbstractResolve;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    if (hasDuplicates(imports)) {
      throw new IllegalStateException("Duplicate import declaration found in " + sourceReference);
    }

    Collector<Resolve, ?, java.util.Map<String, String>> collector = Collectors.toMap(Resolve::key, Resolve::propertyName);

    Map<String, String> propertyNames = resolves.stream().collect(collector);

    List<Resolved> resolveds = mapList(ctx.resolved(), (res) -> resolveResolved(propertyNames, res));
    List<Resolved> locked = mapList(ctx.locked(), (res) -> resolveLocked(propertyNames, res));

    return RangeResolver.of(repositories, imports, resolves, resolveds, locked);
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
    return Resolved.of(
        comments(res.comment()),
        group,
        artifact,
        res.resolvedArtifact().version().getText(),
        res.resolvedFromArtifact().getText(),
        propertyNames.getOrDefault(key, String.format(ARTIFACT_VERSION, artifact)));
  }

  private Resolved resolveResolved(Map<String, String> propertyNames, RangeResolverParser.ResolvedContext res) {
    String group = res.resolvedArtifact().group().getText();
    String artifact = res.resolvedArtifact().artifactId().getText();
    String key = String.format("%s:%s", res.resolvedArtifact().group().getText(), res.resolvedArtifact().artifactId().getText());
    return Resolved.of(
        comments(res.comment()),
        group,
        artifact,
        res.resolvedArtifact().version().getText(),
        res.resolvedFromArtifact().getText(),
        propertyNames.getOrDefault(key, String.format(ARTIFACT_VERSION, artifact)));
  }

  private Import resolveImport(RangeResolverParser.ImportedContext res) {
    return Import.of(
        comments(res.comment()),
        res.resolvedArtifact().group().getText(),
        res.resolvedArtifact().artifactId().getText(),
        res.resolvedArtifact().version().getText());
  }

  private Resolve resolveResolve(RangeResolverParser.ResolveContext res) {
    return Resolve.of(
        comments(res.comment()),
        res.rangedArtifact().group().getText(),
        res.rangedArtifact().artifactId().getText(),
        AbstractResolve.Bound.valueOf(res.bounds().getText().toUpperCase()),
        range(res.rangedArtifact().range()),
        alias(res.alias()));
  }

  private Range range(RangeResolverParser.RangeContext context) {
    return Range.of(
        context.rangeContents().lowerBound().getText(),
        context.rangeContents().lowerBoundValue().getText(),
        context.rangeContents().upperBoundValue().getText(),
        context.rangeContents().upperBound().getText());
  }

  private String comments(List<RangeResolverParser.CommentContext> comment) {
    if (comment == null) {
      return "";
    } else {
      return String.join("\n", mapList(comment, c -> c.getText().trim())).trim();
    }
  }

  private String alias(RangeResolverParser.AliasContext context) {
    return context != null ? context.value().getText() : "";
  }
}
