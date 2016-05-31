package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

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
  public abstract Bound bounds();

  @Value.Parameter
  public abstract Range range();

  @Value.Derived
  public String key() {
    return String.format("%s:%s", group(), artifact());
  }

  @Value.Derived
  public String propertyName() {
    return String.format(ARTIFACT_VERSION, artifact());
  }
}
