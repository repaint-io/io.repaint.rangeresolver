package com.smxemail.rangeresolver.api;

import com.google.common.collect.Multimap;
import javaslang.collection.List;
import org.immutables.javaslang.encodings.JavaslangEncodingEnabled;
import org.immutables.value.Value;

@Value.Immutable
@JavaslangEncodingEnabled
public abstract class AbstractRangeResolver {

  @Value.Parameter
  public abstract Multimap<String, Repository> repositories();

  @Value.Parameter
  public abstract List<Import> imports();

  @Value.Parameter
  public abstract List<Allow> allows();

  @Value.Parameter
  public abstract List<Resolve> resolves();

  @Value.Parameter
  public abstract List<Alias> aliases();

  @Value.Parameter
  public abstract List<Resolved> resolved();

  @Value.Parameter
  public abstract List<Resolved> locked();
}
