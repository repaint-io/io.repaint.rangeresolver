package com.smxemail.rangeresolver.api;

import io.vavr.collection.Set;
import org.immutables.value.Value;

import java.util.regex.Pattern;

@Value.Immutable
public abstract class AbstractImport {

  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract String group();

  @Value.Parameter
  public abstract String artifact();

  @Value.Parameter
  public abstract String version();

  @Value.Parameter
  public abstract Set<Pattern> snapshots();

  @Value.Parameter
  public abstract Set<Pattern> unlocked();

  @Value.Parameter
  public abstract Set<Pattern> withheld();

  @Value.Derived
  public String key() {
    return String.format("%s:%s", group(), artifact());
  }

  @Value.Derived
  public String gav() {
    return String.format("%s:%s:%s", group(), artifact(), version());
  }
}
