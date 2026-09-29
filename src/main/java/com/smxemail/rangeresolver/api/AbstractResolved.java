package com.smxemail.rangeresolver.api;

import dev.zazr.control.Option;

import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.smxemail.rangeresolver.api.ArtifactType.DOCKER;

@Value.Immutable
public abstract class AbstractResolved {
  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract ArtifactType artifactType();

  @Value.Parameter
  public abstract String group();

  @Value.Parameter
  public abstract String artifact();

  @Value.Parameter
  public abstract String version();

  @Value.Parameter
  public abstract Option<String> resolvedFrom();

  @Value.Parameter
  public abstract String propertyName();

  @Value.Parameter
  public abstract Range constrainedRange();

  @Value.Parameter
  public abstract ResolvedType resolutionType();

  @Value.Derived
  public String key() {
    if (artifactType().equals(DOCKER)) {
      return "%s".formatted(artifact());
    } else {
      return "%s:%s".formatted(group(), artifact());
    }
  }

  @Override
  public String toString() {
    return key() + ":" + constrainedRange().asVersionRange();
  }

  public static final Collector<Resolved, ?, Map<String, Resolved>> collectByKey =
      Collectors.toMap(Resolved::key, Function.identity());

  public static Map<String, Resolved> mapByKey(List<Resolved> resolveds) {
    return resolveds.stream().collect(collectByKey);
  }
}
