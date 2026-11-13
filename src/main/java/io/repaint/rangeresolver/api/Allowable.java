package io.repaint.rangeresolver.api;

/// What an `allow` rule permits for the artifacts it matches.
public enum Allowable {
    /// Matching artifacts may resolve to SNAPSHOT versions.
    SNAPSHOTS,
    /// Matching artifacts' locked versions are released for re-resolution.
    UNLOCKED
}
