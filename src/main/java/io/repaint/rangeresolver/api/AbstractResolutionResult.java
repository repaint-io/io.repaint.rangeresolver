package io.repaint.rangeresolver.api;

import dev.zazr.collection.List;

import org.immutables.value.Value;

/// The outcome of resolving a range resolver script against its repositories.
@Value.Immutable
public abstract class AbstractResolutionResult {
    AbstractResolutionResult() {}

    /// The aliases carried over from the script.
    ///
    /// @return the aliases.
    @Value.Parameter
    public abstract List<Alias> aliases();

    /// The resolution requests carried over from the script.
    ///
    /// @return the resolution requests.
    @Value.Parameter
    public abstract List<Resolve> resolves();

    /// The artifacts resolved from `blacklist` requests.
    ///
    /// @return the blacklisted artifacts.
    @Value.Parameter
    public abstract List<Resolved> blacklisted();

    /// The artifacts resolved from `deprecate` requests.
    ///
    /// @return the deprecated artifacts.
    @Value.Parameter
    public abstract List<Resolved> deprecated();

    /// The artifacts resolved from `resolve` and `override` requests.
    ///
    /// @return the resolved artifacts.
    @Value.Parameter
    public abstract List<Resolved> resolved();

    /// The artifacts kept at their previously locked versions.
    ///
    /// @return the locked artifacts.
    @Value.Parameter
    public abstract List<Resolved> locked();
}
