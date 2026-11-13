package io.repaint.rangeresolver.api;

import dev.zazr.collection.Set;

import org.immutables.value.Value;

import java.util.regex.Pattern;

/// An `import group:artifact:version;` statement, which pulls in the resolutions published by another project's
/// bill of materials.
@Value.Immutable
public abstract class AbstractImport {
    AbstractImport() {}

    /// The comments preceding the statement in the source script.
    ///
    /// @return the comment text, or an empty string if there were none.
    @Value.Parameter
    public abstract String comments();

    /// The Maven group id of the imported bill of materials.
    ///
    /// @return the group id.
    @Value.Parameter
    public abstract String group();

    /// The Maven artifact id of the imported bill of materials.
    ///
    /// @return the artifact id.
    @Value.Parameter
    public abstract String artifact();

    /// The version of the imported bill of materials.
    ///
    /// @return the version.
    @Value.Parameter
    public abstract String version();

    /// Patterns for imported artifact keys that may resolve to SNAPSHOT versions.
    ///
    /// @return the snapshot patterns.
    @Value.Parameter
    public abstract Set<Pattern> snapshots();

    /// Patterns for imported artifact keys whose locked versions are released for re-resolution.
    ///
    /// @return the unlocked patterns.
    @Value.Parameter
    public abstract Set<Pattern> unlocked();

    /// Patterns for imported artifact keys that are withheld from unlocking.
    ///
    /// @return the withheld patterns.
    @Value.Parameter
    public abstract Set<Pattern> withheld();

    /// The `group:artifact` key identifying the imported artifact.
    ///
    /// @return the key.
    @Value.Derived
    public String key() {
        return "%s:%s".formatted(group(), artifact());
    }

    /// The full `group:artifact:version` coordinates of the imported artifact.
    ///
    /// @return the coordinates.
    @Value.Derived
    public String gav() {
        return "%s:%s:%s".formatted(group(), artifact(), version());
    }
}
