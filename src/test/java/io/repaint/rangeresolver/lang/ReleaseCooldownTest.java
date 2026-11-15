package io.repaint.rangeresolver.lang;

import dev.zazr.collection.List;

import io.repaint.rangeresolver.api.Bound;
import io.repaint.rangeresolver.api.Range;
import io.repaint.rangeresolver.api.Resolve;
import io.repaint.rangeresolver.api.ResolveType;

import org.apache.maven.repository.supplier.RepositorySystemSupplier;
import org.apache.maven.repository.supplier.SessionBuilderSupplier;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseCooldownTest {
    private static final Instant NOW = Instant.parse("2026-11-15T00:00:00Z");
    private static final GenericVersionScheme SCHEME = new GenericVersionScheme();

    @TempDir
    Path repoDir;

    private final Resolve resolve = Resolve.of(
            "",
            "com.example",
            "widget",
            "",
            "jar",
            Bound.HIGHEST,
            Range.excludingUpper("1.0", "2.0"),
            ResolveType.RESOLVE,
            Pattern.compile("$^"),
            "test",
            "default");

    @Test
    void holdsBackVersionsPublishedWithinTheCooldown() throws Exception {
        publish("1.0", NOW.minus(Duration.ofDays(30)));
        publish("1.1", NOW.minus(Duration.ofDays(10)));
        publish("1.2", NOW.minus(Duration.ofDays(2)));

        assertThat(apply(7, "1.0", "1.1", "1.2")).containsExactly("1.0", "1.1");
        assertThat(apply(14, "1.0", "1.1", "1.2")).containsExactly("1.0");
        assertThat(apply(60, "1.0", "1.1", "1.2")).isEmpty();
    }

    @Test
    void holdsBackVersionsWithUnknownPublishDates() throws Exception {
        publish("1.0", NOW.minus(Duration.ofDays(30)));
        // 1.1 has no POM in the repository.

        assertThat(apply(7, "1.0", "1.1")).containsExactly("1.0");
    }

    @Test
    void disabledCooldownKeepsEverything() throws Exception {
        assertThat(apply(0, "1.0", "1.1")).containsExactly("1.0", "1.1");
    }

    @Test
    void asksRemoteRepositoriesForLocallyInstalledVersions() throws Exception {
        publish("1.0", NOW.minus(Duration.ofDays(30)));
        publish("1.1", NOW.minus(Duration.ofDays(10)));
        publish("1.2", NOW.minus(Duration.ofDays(2)));

        // A local install lists the versions in maven-metadata-local.xml, so they are attributed to the local
        // repository rather than the remote one they were also published to.
        assertThat(apply(7, true, "1.0", "1.1", "1.2")).containsExactly("1.0", "1.1");
    }

    private java.util.List<String> apply(int days, String... versions) throws InvalidVersionSpecificationException {
        return apply(days, false, versions);
    }

    private java.util.List<String> apply(int days, boolean foundLocally, String... versions)
            throws InvalidVersionSpecificationException {
        RemoteRepository repository =
                new RemoteRepository.Builder("test", "default", repoDir.toUri().toString()).build();
        RepositorySystemSession session = session();
        VersionRangeRequest request = new VersionRangeRequest().addRepository(repository);
        VersionRangeResult rangeResult = new VersionRangeResult(request);
        java.util.List<Version> parsed = new ArrayList<>();
        for (String version : versions) {
            Version v = SCHEME.parseVersion(version);
            parsed.add(v);
            rangeResult.addVersion(v);
            rangeResult.setRepository(v, foundLocally ? session.getLocalRepository() : repository);
        }

        ReleaseCooldown cooldown = days == 0
                ? ReleaseCooldown.disabled()
                : new ReleaseCooldown(Duration.ofDays(days), Clock.fixed(NOW, ZoneOffset.UTC), null, s -> {}, s -> {});

        return cooldown.apply(session, rangeResult, resolve, List.ofAll(parsed))
                .map(Version::toString)
                .asJava();
    }

    private void publish(String version, Instant publishedAt) throws IOException {
        Path pom = repoDir.resolve("com/example/widget/%s/widget-%s.pom".formatted(version, version));
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, "<project/>");
        Files.setLastModifiedTime(pom, FileTime.from(publishedAt));
    }

    private RepositorySystemSession session() {
        RepositorySystem system = new RepositorySystemSupplier().get();
        return new SessionBuilderSupplier(system)
                .get()
                .withLocalRepositoryBaseDirectories(repoDir.resolve("local"))
                .build();
    }
}
