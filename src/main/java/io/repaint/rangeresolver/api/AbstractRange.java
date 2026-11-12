package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

import static io.repaint.rangeresolver.lang.RepositoryResolver.isNewerVersion;

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

  public static Range excludingUpper(String lower, String upper) {
    return Range.of("[", lower, upper, ")");
  }

  @Value.Check
  protected void check() {
    if (lower().isEmpty() || upper().isEmpty()) {
      // A half-open range such as [1.0,) or (,2.0] has nothing to compare.
      return;
    }
    if (isNewerVersion(lower(), upper())) {
      throw new IllegalArgumentException(
          "Invalid range requested, %s is lower than %s".formatted(upper(), lower()));
    }
  }

  public String asVersionRange() {
    if (lower().equals(upper())) {
      return "[%s]".formatted(lower());
    } else {
      return "%s%s,%s%s".formatted(lowerChar(), lower(), upper(), upperChar());
    }
  }
}
