package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

@Value.Immutable
public abstract class AbstractAlias implements PropertyBased {
  @Value.Parameter
  public abstract String comments();

  @Value.Parameter
  public abstract String group();

  @Value.Parameter
  public abstract String artifact();

  @Value.Parameter
  public abstract String alias();

  @Override
  @Value.Derived
  public String key() {
    return "%s:%s".formatted(group(), artifact());
  }

  @Override
  public String propertyName() {
    return alias();
  }
}
