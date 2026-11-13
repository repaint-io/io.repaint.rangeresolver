package io.repaint.rangeresolver.api;

import dev.zazr.control.Option;

import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static io.repaint.rangeresolver.api.ArtifactType.DOCKER;

/// An artifact pinned to a concrete version, such as a `resolved group:artifact:1.2.3;` statement.
@Value.Immutable
public abstract class AbstractResolved {
    AbstractResolved() {}

    /// The comments preceding the statement in the source script.
    ///
    /// @return the comment text, or an empty string if there were none.
    @Value.Parameter
    public abstract String comments();

    /// Whether this is a Maven artifact or a Docker image.
    ///
    /// @return the artifact type.
    @Value.Parameter
    public abstract ArtifactType artifactType();

    /// The Maven group id.
    ///
    /// @return the group id.
    @Value.Parameter
    public abstract String group();

    /// The Maven artifact id, or the Docker image name.
    ///
    /// @return the artifact id.
    @Value.Parameter
    public abstract String artifact();

    /// The resolved version, or for Docker the image digest.
    ///
    /// @return the version.
    @Value.Parameter
    public abstract String version();

    /// Where the resolution came from, such as the imported bill of materials that supplied it.
    ///
    /// @return the origin, if known.
    @Value.Parameter
    public abstract Option<String> resolvedFrom();

    /// The property name the version is published under.
    ///
    /// @return the property name.
    @Value.Parameter
    public abstract String propertyName();

    /// The range the version was resolved within.
    ///
    /// @return the range.
    @Value.Parameter
    public abstract Range constrainedRange();

    /// How the artifact is recorded in the script.
    ///
    /// @return the resolution type.
    @Value.Parameter
    public abstract ResolvedType resolutionType();

    /// The key identifying the artifact: `group:artifact` for Maven, the image name for Docker.
    ///
    /// @return the key.
    @Value.Derived
    public String key() {
        if (artifactType().equals(DOCKER)) {
            return "%s".formatted(artifact());
        } else {
            return "%s:%s".formatted(group(), artifact());
        }
    }

    @Override
    public String toString() {
        return key() + ":" + constrainedRange().asVersionRange();
    }

    /// Collects resolved artifacts into a map keyed by [#key()].
    public static final Collector<Resolved, ?, Map<String, Resolved>> collectByKey =
            Collectors.toMap(Resolved::key, Function.identity());

    /// Indexes resolved artifacts by [#key()].
    ///
    /// @param resolveds the artifacts to index.
    /// @return the artifacts keyed by their key.
    /// @throws IllegalStateException if two artifacts share a key.
    public static Map<String, Resolved> mapByKey(List<Resolved> resolveds) {
        return resolveds.stream().collect(collectByKey);
    }
}
