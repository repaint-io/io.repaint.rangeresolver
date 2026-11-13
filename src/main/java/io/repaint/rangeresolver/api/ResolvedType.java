package io.repaint.rangeresolver.api;

/// How a pinned artifact is recorded in a range resolver script.
public enum ResolvedType {
    /// A `resolved` statement: the current resolution of a range.
    RESOLVED,
    /// A `locked` statement: a version kept until explicitly unlocked.
    LOCKED,
    /// A `deprecated` statement: the resolution of a `deprecate` request.
    DEPRECATED,
    /// A `blacklisted` statement: the resolution of a `blacklist` request.
    BLACKLISTED
}
