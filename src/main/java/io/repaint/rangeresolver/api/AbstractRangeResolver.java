package io.repaint.rangeresolver.api;

import com.google.common.collect.Multimap;

import dev.zazr.collection.List;
import dev.zazr.collection.Set;

import org.immutables.value.Value;

/// The model of a parsed range resolver script.
@Value.Immutable
public abstract class AbstractRangeResolver {
    AbstractRangeResolver() {}

    /// The declared repositories, keyed by grouping name.
    ///
    /// @return the repositories.
    @Value.Parameter
    public abstract Multimap<String, Repository> repositories();

    /// The imported bills of materials, in declaration order.
    ///
    /// @return the imports.
    @Value.Parameter
    public abstract List<Import> imports();

    /// The `allow` rules, including any supplied on the command line.
    ///
    /// @return the allow rules.
    @Value.Parameter
    public abstract Set<Allow> allows();

    /// The `withhold` rules.
    ///
    /// @return the withhold rules.
    @Value.Parameter
    public abstract Set<Allow> withheld();

    /// The resolution requests (`resolve`, `override`, `deprecate`, `blacklist` and `resolve docker`).
    ///
    /// @return the resolution requests.
    @Value.Parameter
    public abstract List<Resolve> resolves();

    /// The `alias` statements.
    ///
    /// @return the aliases.
    @Value.Parameter
    public abstract List<Alias> aliases();

    /// The artifacts recorded as `resolved`.
    ///
    /// @return the resolved artifacts.
    @Value.Parameter
    public abstract List<Resolved> resolved();

    /// The artifacts recorded as `deprecated`.
    ///
    /// @return the deprecated artifacts.
    @Value.Parameter
    public abstract List<Resolved> deprecated();

    /// The artifacts recorded as `blacklisted`.
    ///
    /// @return the blacklisted artifacts.
    @Value.Parameter
    public abstract List<Resolved> blacklisted();

    /// The artifacts recorded as `locked`.
    ///
    /// @return the locked artifacts.
    @Value.Parameter
    public abstract List<Resolved> locked();
}
