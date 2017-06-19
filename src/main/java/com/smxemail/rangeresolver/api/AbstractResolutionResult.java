package com.smxemail.rangeresolver.api;

import io.vavr.collection.List;
import org.immutables.value.Value;

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
