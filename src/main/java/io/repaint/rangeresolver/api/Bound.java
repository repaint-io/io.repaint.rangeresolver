package io.repaint.rangeresolver.api;

/// Which version to choose from those available within a range.
public enum Bound {
    /// The newest available version, SNAPSHOT versions excluded unless filtering says otherwise.
    HIGHEST,
    /// The oldest available version, SNAPSHOT versions excluded unless filtering says otherwise.
    LOWEST,
    /// The newest available version, SNAPSHOT versions included.
    SNAPSHOT
}
