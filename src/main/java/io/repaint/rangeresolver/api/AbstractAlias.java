package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

/// An `alias group:artifact as name;` statement, which publishes an artifact's resolved version under an alternative
/// property name.
@Value.Immutable
public abstract class AbstractAlias implements PropertyBased {
  AbstractAlias() {}

  /// The comments preceding the statement in the source script.
  ///
  /// @return the comment text, or an empty string if there were none.
  @Value.Parameter
  public abstract String comments();

  /// The Maven group id of the aliased artifact.
  ///
  /// @return the group id.
  @Value.Parameter
  public abstract String group();

  /// The Maven artifact id of the aliased artifact.
  ///
  /// @return the artifact id.
  @Value.Parameter
  public abstract String artifact();

  /// The alternative property name to publish the version under.
  ///
  /// @return the alias.
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
