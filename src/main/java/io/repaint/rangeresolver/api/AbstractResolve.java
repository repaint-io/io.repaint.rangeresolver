package io.repaint.rangeresolver.api;

import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static io.repaint.rangeresolver.api.ResolveType.DOCKER;
import static io.repaint.rangeresolver.lang.RangeResolverVisitor.ARTIFACT_VERSION;

/// A request to resolve an artifact's version from a range, such as `resolve highest group:artifact:[1.0,2.0);`.
@Value.Immutable
public abstract class AbstractResolve implements PropertyBased {
    AbstractResolve() {}

    /// The comments preceding the statement in the source script.
    ///
    /// @return the comment text, or an empty string if there were none.
    @Value.Parameter
    public abstract String comments();

    /// The Maven group id to resolve.
    ///
    /// @return the group id.
    @Value.Parameter
    public abstract String group();

    /// The Maven artifact id, or for Docker the `image:tag` to resolve.
    ///
    /// @return the artifact id.
    @Value.Parameter
    public abstract String artifact();

    /// The Maven classifier to resolve.
    ///
    /// @return the classifier, or an empty string for none.
    @Value.Parameter
    public abstract String classifier();

    /// The Maven extension (packaging type) to resolve.
    ///
    /// @return the extension.
    @Value.Parameter
    public abstract String extension();

    /// Which version within the range to choose.
    ///
    /// @return the bound.
    @Value.Parameter
    public abstract Bound bounds();

    /// The requested version range.
    ///
    /// @return the range.
    @Value.Parameter
    public abstract Range range();

    /// The kind of statement this request came from.
    ///
    /// @return the resolve type.
    @Value.Parameter
    public abstract ResolveType resolveType();

    /// A pattern for versions to exclude from resolution.
    ///
    /// @return the exclusion pattern.
    @Value.Parameter
    public abstract Pattern filtering();

    /// The location of the script that declared this request.
    ///
    /// @return the source reference.
    @Value.Parameter
    public abstract String sourceReference();

    /// The repository grouping to resolve against, selected with `via`.
    ///
    /// @return the grouping name, `default` if none was given.
    @Value.Parameter
    public abstract String grouping();

    @Override
    @Value.Derived
    public String key() {
        if (resolveType().equals(DOCKER)) {
            return "%s".formatted(stripDockerTagArtifact());
        } else {
            return "%s:%s".formatted(group(), artifact());
        }
    }

    @Override
    @Value.Derived
    public String propertyName() {
        if (resolveType().equals(DOCKER)) {
            return ARTIFACT_VERSION.formatted(stripDockerTagArtifact());
        } else {
            return ARTIFACT_VERSION.formatted(artifact());
        }
    }

    private String stripDockerTagArtifact() {
        return artifact().substring(0, artifact().indexOf(":"));
    }

    /// Collects resolution requests into a map keyed by [#key()].
    public static final Collector<Resolve, ?, Map<String, Resolve>> collectByKey =
            Collectors.toMap(Resolve::key, Function.identity());

    /// Indexes resolution requests by [#key()].
    ///
    /// @param resolves the requests to index.
    /// @return the requests keyed by their key.
    /// @throws IllegalStateException if two requests share a key.
    public static Map<String, Resolve> mapByKey(List<Resolve> resolves) {
        return resolves.stream().collect(collectByKey);
    }
}
