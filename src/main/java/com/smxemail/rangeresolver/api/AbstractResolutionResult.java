package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public abstract class AbstractResolutionResult {

  @Value.Parameter
  public abstract List<Alias> aliases();

  @Value.Parameter
  public abstract List<Resolve> resolves();

  @Value.Parameter
  public abstract List<Resolved> resolved();

  @Value.Parameter
  public abstract List<Resolved> locked();
}
