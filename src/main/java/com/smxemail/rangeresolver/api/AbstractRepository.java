package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

@Value.Immutable
public abstract class AbstractRepository {

  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract String url();

  @Value.Parameter
  public abstract String grouping();
}
