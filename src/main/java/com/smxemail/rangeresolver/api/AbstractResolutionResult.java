package com.smxemail.rangeresolver.api;

import javaslang.collection.List;
import org.immutables.javaslang.encodings.JavaslangEncodingEnabled;
import org.immutables.value.Value;

@Value.Immutable
@JavaslangEncodingEnabled
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
