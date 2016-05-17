package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

@Value.Immutable
public abstract class AbstractResolved {

  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract String group();

  @Value.Parameter
  public abstract String artifact();

  @Value.Parameter
  public abstract String version();

  @Value.Parameter
  public abstract String resolvedFrom();

  @Value.Parameter
  public abstract String propertyName();

  @Value.Derived
  public String key() {
    return String.format("%s:%s", group(), artifact());
  }
}
