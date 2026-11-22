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
import org.eclipse.aether.repository.ArtifactRepository;
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
import java.util.function.Function;
import java.util.regex.Pattern;

import static dev.zazr.control.Option.none;

import static io.repaint.rangeresolver.lang.RepositoryResolver.cooldownsFor;

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

    @Test
    void usesTheCooldownOfTheRepositoryEachVersionWasFoundIn() throws Exception {
        Path trusted = repoDir.resolve("trusted");
        publish("1.0", NOW.minus(Duration.ofDays(30)));
        publish("1.1", NOW.minus(Duration.ofDays(2)));
        publish(trusted, "1.2", NOW.minus(Duration.ofDays(1)));

        RemoteRepository cautious = repository("cautious", repoDir);
        RemoteRepository internal = repository("internal", trusted);
        Function<RemoteRepository, Duration> cooldowns =
                r -> r.getId().equals("cautious") ? Duration.ofDays(7) : Duration.ZERO;

        // 1.2 comes from a repository with no cooldown, so it is kept without its publish date being checked.
        assertThat(apply(cooldowns, java.util.Map.of("1.0", cautious, "1.1", cautious, "1.2", internal)))
                .containsExactly("1.0", "1.1", "1.2");

        // Without 1.2, 1.1 is within the cooldown of the repository it was found in.
        assertThat(apply(cooldowns, java.util.Map.of("1.0", cautious, "1.1", cautious)))
                .containsExactly("1.0");
    }

    @Test
    void aStatementCooldownReplacesTheRepositoryCooldown() throws Exception {
        publish("1.0", NOW.minus(Duration.ofDays(30)));
        publish("1.1", NOW.minus(Duration.ofDays(10)));
        publish("1.2", NOW.minus(Duration.ofDays(2)));

        RemoteRepository cautious = repository("cautious", repoDir);
        Function<RemoteRepository, Duration> repositoryCooldowns = r -> Duration.ofDays(7);
        var found = java.util.Map.of("1.0", cautious, "1.1", cautious, "1.2", cautious);

        // The repository cooldown alone lets 1.1 through.
        assertThat(apply(cooldownsFor(resolve, repositoryCooldowns, none()), found))
                .containsExactly("1.0", "1.1");

        // A longer cooldown on the statement holds it back.
        assertThat(apply(cooldownsFor(resolve.withCooldown(14), repositoryCooldowns, none()), found))
                .containsExactly("1.0");

        // A zero cooldown on the statement exempts it.
        assertThat(apply(cooldownsFor(resolve.withCooldown(0), repositoryCooldowns, none()), found))
                .containsExactly("1.0", "1.1", "1.2");
    }

    @Test
    void keepsVersionsWithUnknownPublishDatesFromRepositoriesWithoutACooldown() throws Exception {
        // No POMs are published, so any date lookup would fail and hold the versions back.
        assertThat(apply(0, true, "1.0", "1.1")).containsExactly("1.0", "1.1");
    }

    private java.util.List<String> apply(int days, String... versions) throws InvalidVersionSpecificationException {
        return apply(days, false, versions);
    }

    private java.util.List<String> apply(int days, boolean foundLocally, String... versions)
            throws InvalidVersionSpecificationException {
        RemoteRepository repository = repository("test", repoDir);
        RepositorySystemSession session = session();
        java.util.Map<String, ArtifactRepository> found = new java.util.LinkedHashMap<>();
        for (String version : versions) {
            found.put(version, foundLocally ? session.getLocalRepository() : repository);
        }
        return apply(session, java.util.List.of(repository), r -> Duration.ofDays(days), found);
    }

    private java.util.List<String> apply(
            Function<RemoteRepository, Duration> cooldowns, java.util.Map<String, RemoteRepository> found)
            throws InvalidVersionSpecificationException {
        java.util.List<RemoteRepository> repositories = new ArrayList<>(new java.util.LinkedHashSet<>(found.values()));
        return apply(session(), repositories, cooldowns, new java.util.TreeMap<String, ArtifactRepository>(found));
    }

    private java.util.List<String> apply(
            RepositorySystemSession session,
            java.util.List<RemoteRepository> repositories,
            Function<RemoteRepository, Duration> cooldowns,
            java.util.Map<String, ArtifactRepository> found)
            throws InvalidVersionSpecificationException {
        VersionRangeRequest request = new VersionRangeRequest().setRepositories(repositories);
        VersionRangeResult rangeResult = new VersionRangeResult(request);
        java.util.List<Version> parsed = new ArrayList<>();
        for (java.util.Map.Entry<String, ArtifactRepository> entry : found.entrySet()) {
            Version v = SCHEME.parseVersion(entry.getKey());
            parsed.add(v);
            rangeResult.addVersion(v);
            rangeResult.setRepository(v, entry.getValue());
        }
        parsed.sort(null);

        ReleaseCooldown cooldown = new ReleaseCooldown(Clock.fixed(NOW, ZoneOffset.UTC), () -> null, s -> {}, s -> {});

        return cooldown.apply(session, rangeResult, resolve, List.ofAll(parsed), cooldowns)
                .map(Version::toString)
                .asJava();
    }

    private static RemoteRepository repository(String id, Path dir) {
        return new RemoteRepository.Builder(id, "default", dir.toUri().toString()).build();
    }

    private void publish(String version, Instant publishedAt) throws IOException {
        publish(repoDir, version, publishedAt);
    }

    private static void publish(Path repoDir, String version, Instant publishedAt) throws IOException {
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
