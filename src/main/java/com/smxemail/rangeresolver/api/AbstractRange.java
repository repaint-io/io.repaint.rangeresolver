package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

@Value.Immutable
public abstract class AbstractRange {

  @Value.Parameter
  public abstract String lowerChar();

  @Value.Parameter
  public abstract String lower();

  @Value.Parameter
  public abstract String upper();

  @Value.Parameter
  public abstract String upperChar();

  public static Range fixed(String version) {
    return Range.of("[", version, version, "]");
  }

  public String asVersionRange() {
    return String.format("%s%s,%s%s", lowerChar(), lower(), upper(), upperChar());
  }
}
