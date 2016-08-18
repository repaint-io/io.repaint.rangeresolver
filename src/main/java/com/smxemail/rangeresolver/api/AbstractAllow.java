package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

import java.util.regex.Pattern;

@Value.Immutable
public abstract class AbstractAllow {

  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract Allowable allowable();

  @Value.Parameter
  public abstract Pattern pattern();
}
