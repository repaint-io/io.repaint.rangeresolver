package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

import java.util.regex.Pattern;

import static java.util.Comparator.comparing;
import static java.util.Objects.compare;

@Value.Immutable
public abstract class AbstractAllow implements Comparable<AbstractAllow> {

  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract Allowable allowable();

  @Value.Parameter
  public abstract Pattern pattern();

  @Value.Parameter
  public abstract boolean persistent();

  @Override
  public int compareTo(AbstractAllow o) {
    return compare(
        this, o, comparing(AbstractAllow::allowable).thenComparing(aa -> aa.pattern().toString()));
  }
}
