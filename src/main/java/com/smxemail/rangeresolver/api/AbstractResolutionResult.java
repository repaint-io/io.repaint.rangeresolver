package com.smxemail.rangeresolver.api;

import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Value.Immutable
public abstract class AbstractResolutionResult {

  @Value.Parameter
  public abstract List<Alias> aliases();

  @Value.Parameter
  public abstract List<Resolved> resolved();

  @Value.Parameter
  public abstract List<Resolved> locked();

  public ResolutionResult append(ResolutionResult other) {
    return ResolutionResult.combine(this, other);
  }

  public static ResolutionResult combine(AbstractResolutionResult left, AbstractResolutionResult right) {
    return ResolutionResult.of(combine(left.aliases(), right.aliases()), combine(left.resolved(), right.resolved()), combine(left.locked(), right.locked()));
  }

  private static <T> List<T> combine(List<T> left, List<T> right) {
    return ImmutableList.<T>builder().addAll(left).addAll(right).build();
  }

  public static ResolutionResult empty() {
    return ResolutionResult.of(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  public static final Collector<ResolutionResult, ?, ResolutionResult> REDUCING =
      Collectors.reducing(AbstractResolutionResult.empty(), AbstractResolutionResult::combine);
}
