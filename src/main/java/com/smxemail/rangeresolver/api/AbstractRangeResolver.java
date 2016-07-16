package com.smxemail.rangeresolver.api;

import com.google.common.collect.Multimap;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public abstract class AbstractRangeResolver {

  @Value.Parameter
  public abstract Multimap<String, Repository> repositories();

  @Value.Parameter
  public abstract List<Import> imports();

  @Value.Parameter
  public abstract List<Resolve> resolves();

  @Value.Parameter
  public abstract List<Alias> aliases();

  @Value.Parameter
  public abstract List<Resolved> resolved();

  @Value.Parameter
  public abstract List<Resolved> locked();
}
