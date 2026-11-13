package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

import static io.repaint.rangeresolver.lang.RepositoryResolver.isNewerVersion;

/// A Maven version range such as `[1.0,2.0)`, kept as its bracket characters and bound values.
@Value.Immutable
public abstract class AbstractRange {
    AbstractRange() {}

    /// The opening bracket, `[` for an inclusive lower bound or `(` for an exclusive one.
    ///
    /// @return the opening bracket.
    @Value.Parameter
    public abstract String lowerChar();

    /// The lower bound version.
    ///
    /// @return the lower bound, or an empty string if the range has no lower bound.
    @Value.Parameter
    public abstract String lower();

    /// The upper bound version.
    ///
    /// @return the upper bound, or an empty string if the range has no upper bound.
    @Value.Parameter
    public abstract String upper();

    /// The closing bracket, `]` for an inclusive upper bound or `)` for an exclusive one.
    ///
    /// @return the closing bracket.
    @Value.Parameter
    public abstract String upperChar();

    /// Creates a range matching exactly one version.
    ///
    /// @param version the version to match.
    /// @return the range `[version,version]`.
    public static Range fixed(String version) {
        return Range.of("[", version, version, "]");
    }

    /// Creates a range including its lower bound and excluding its upper bound.
    ///
    /// @param lower the inclusive lower bound.
    /// @param upper the exclusive upper bound.
    /// @return the range `[lower,upper)`.
    public static Range excludingUpper(String lower, String upper) {
        return Range.of("[", lower, upper, ")");
    }

    /// Rejects a range whose lower bound is newer than its upper bound.
    ///
    /// @throws IllegalArgumentException if the bounds are inverted.
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

    /// Formats the range in Maven version range syntax.
    ///
    /// @return `[version]` for a single version, otherwise the full range such as `[1.0,2.0)`.
    public String asVersionRange() {
        if (lower().equals(upper())) {
            return "[%s]".formatted(lower());
        } else {
            return "%s%s,%s%s".formatted(lowerChar(), lower(), upper(), upperChar());
        }
    }
}
