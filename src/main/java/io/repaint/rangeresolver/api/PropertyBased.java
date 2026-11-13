package io.repaint.rangeresolver.api;

/// A statement whose resolved version is published as a property.
public interface PropertyBased {
    /// The key identifying the artifact, normally `group:artifact`.
    ///
    /// @return the key.
    String key();

    /// The name of the property the resolved version is published under.
    ///
    /// @return the property name.
    String propertyName();
}
