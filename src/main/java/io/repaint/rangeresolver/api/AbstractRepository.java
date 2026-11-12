package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

/// A `repository url [as grouping];` statement declaring a remote Maven repository.
@Value.Immutable
public abstract class AbstractRepository {
  AbstractRepository() {}

  /// The comments preceding the statement in the source script.
  ///
  /// @return the comment text, or an empty string if there were none.
  @Value.Parameter
  public abstract String comments();

  /// The repository URL.
  ///
  /// @return the URL.
  @Value.Parameter
  public abstract String url();

  /// The grouping name that resolutions select repositories by with `via`.
  ///
  /// @return the grouping name, `default` if none was given.
  @Value.Parameter
  public abstract String grouping();
}
