package io.repaint.rangeresolver.api;

import dev.zazr.collection.List;

/// The kind of statement a resolution request came from.
public enum ResolveType {
    /// A bill of materials pulled in by an `import` statement.
    IMPORT,
    /// A `resolve` statement.
    RESOLVE,
    /// An `override` statement, which replaces an imported resolution.
    OVERRIDE,
    /// A `deprecate` statement.
    DEPRECATE,
    /// A `blacklist` statement.
    BLACKLIST,
    /// A `resolve docker` statement.
    DOCKER;

    /// Selects the resolution requests of this type.
    ///
    /// @param rangeResolver the model to select from.
    /// @return the matching requests, in declaration order.
    public List<Resolve> from(RangeResolver rangeResolver) {
        return rangeResolver.resolves().filter(res -> res.resolveType() == this);
    }
}
