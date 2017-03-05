package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.ARTIFACT_VERSION;

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
  public abstract Pattern filtering();

  @Value.Parameter
  public abstract String sourceReference();

  @Value.Parameter
  public abstract String grouping();

  @Value.Derived
  public String key() {
    return String.format("%s:%s", group(), artifact());
  }

  @Value.Derived
  public String propertyName() {
    return String.format(ARTIFACT_VERSION, artifact());
  }

  public static final Collector<Resolve, ?, Map<String, Resolve>> collectByKey =
      Collectors.toMap(Resolve::key, Function.identity());

  public static final Map<String, Resolve> mapByKey(List<Resolve> resolves) {
    return resolves.stream().collect(collectByKey);
  }
}
