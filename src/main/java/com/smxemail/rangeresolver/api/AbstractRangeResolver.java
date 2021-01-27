package com.smxemail.rangeresolver.api;

import com.google.common.collect.Multimap;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import org.immutables.value.Value;

@Value.Immutable
public abstract class AbstractRangeResolver {

  @Value.Parameter
  public abstract Multimap<String, Repository> repositories();

  @Value.Parameter
  public abstract List<Import> imports();

  @Value.Parameter
  public abstract Set<Allow> allows();

  @Value.Parameter
  public abstract Set<Allow> withheld();

  @Value.Parameter
  public abstract List<Resolve> resolves();

  @Value.Parameter
  public abstract List<Alias> aliases();

  @Value.Parameter
  public abstract List<Resolved> resolved();

  @Value.Parameter
  public abstract List<Resolved> deprecated();

  @Value.Parameter
  public abstract List<Resolved> blacklisted();

  @Value.Parameter
  public abstract List<Resolved> locked();
}
