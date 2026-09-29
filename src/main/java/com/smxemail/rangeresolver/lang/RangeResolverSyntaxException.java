package com.smxemail.rangeresolver.lang;

import java.io.Serial;
import java.util.List;

/// Thrown when a range resolver script cannot be parsed. Previously syntax errors were reported to stderr by ANTLR's
/// default listener and then silently recovered from, which meant a malformed script produced a partial model rather
/// than a failure.
public class RangeResolverSyntaxException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final List<String> errors;

  RangeResolverSyntaxException(String message, List<String> errors) {
    super(message);
    this.errors = List.copyOf(errors);
  }

  /// The individual syntax errors, in the order the parser reported them.
  public List<String> errors() {
    return errors;
  }
}
