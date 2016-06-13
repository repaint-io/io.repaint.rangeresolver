package com.smxemail.rangeresolver.api;

import org.immutables.value.Value;

import static com.smxemail.rangeresolver.lang.RepositoryResolver.isNewerVersion;

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

  @Value.Check
  protected void check() {
    if (isNewerVersion(lower(), upper())) {
      throw new IllegalArgumentException(String.format("Invalid range requested, %s is lower than %s", upper(), lower()));
    }
  }

  public String asVersionRange() {
    return String.format("%s%s,%s%s", lowerChar(), lower(), upper(), upperChar());
  }
}
