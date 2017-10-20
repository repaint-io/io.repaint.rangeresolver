package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class AbstractAllow implements Comparable<AbstractAllow> {

  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract Allowable allowable();

  @Value.Parameter
  public abstract Pattern pattern();

  @Override
  public int compareTo(AbstractAllow o) {
    return Objects.compare(this, o,
        Comparator.comparing(AbstractAllow::allowable)
                  .thenComparing(aa -> aa.pattern().toString())
    );
  }
}
