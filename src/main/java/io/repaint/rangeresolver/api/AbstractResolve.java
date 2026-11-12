package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static io.repaint.rangeresolver.api.ResolveType.DOCKER;
import static io.repaint.rangeresolver.lang.RangeResolverVisitor.ARTIFACT_VERSION;

@Value.Immutable
public abstract class AbstractResolve implements PropertyBased {
  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract String group();

  @Value.Parameter
  public abstract String artifact();

  @Value.Parameter
  public abstract String classifier();

  @Value.Parameter
  public abstract String extension();

  @Value.Parameter
  public abstract Bound bounds();

  @Value.Parameter
  public abstract Range range();

  @Value.Parameter
  public abstract ResolveType resolveType();

  @Value.Parameter
  public abstract Pattern filtering();

  @Value.Parameter
  public abstract String sourceReference();

  @Value.Parameter
  public abstract String grouping();

  @Override
  @Value.Derived
  public String key() {
    if (resolveType().equals(DOCKER)) {
      return "%s".formatted(stripDockerTagArtifact());
    } else {
      return "%s:%s".formatted(group(), artifact());
    }
  }

  @Override
  @Value.Derived
  public String propertyName() {
    if (resolveType().equals(DOCKER)) {
      return ARTIFACT_VERSION.formatted(stripDockerTagArtifact());
    } else {
      return ARTIFACT_VERSION.formatted(artifact());
    }
  }

  private String stripDockerTagArtifact() {
    return artifact().substring(0, artifact().indexOf(":"));
  }

  public static final Collector<Resolve, ?, Map<String, Resolve>> collectByKey =
      Collectors.toMap(Resolve::key, Function.identity());

  public static Map<String, Resolve> mapByKey(List<Resolve> resolves) {
    return resolves.stream().collect(collectByKey);
  }
}
