package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

import java.util.regex.Pattern;

import static java.util.Comparator.comparing;
import static java.util.Objects.compare;

/// An `allow` or `withhold` rule, matching artifact keys by regular expression.
@Value.Immutable
public abstract class AbstractAllow implements Comparable<AbstractAllow> {
    AbstractAllow() {}

    /// The comments preceding the statement in the source script.
    ///
    /// @return the comment text, or an empty string if there were none.
    @Value.Parameter
    public abstract String comments();

    /// What this rule permits for matching artifacts.
    ///
    /// @return the kind of allowance.
    @Value.Parameter
    public abstract Allowable allowable();

    /// The pattern matched against artifact keys (`group:artifact`).
    ///
    /// @return the key pattern.
    @Value.Parameter
    public abstract Pattern pattern();

    /// Whether the rule came from the script itself and should be written back out. Rules supplied on the command line
    /// are not persistent.
    ///
    /// @return `true` if the rule is written back to the script.
    @Value.Parameter
    public abstract boolean persistent();

    @Override
    public int compareTo(AbstractAllow o) {
        return compare(
                this,
                o,
                comparing(AbstractAllow::allowable)
                        .thenComparing(aa -> aa.pattern().toString()));
    }
}
