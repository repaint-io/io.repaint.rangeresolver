package io.repaint.rangeresolver.lang;

import dev.zazr.collection.List;
import dev.zazr.control.Option;

import io.repaint.rangeresolver.api.Resolve;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static dev.zazr.control.Option.none;
import static dev.zazr.control.Option.some;

/// Holds back versions published less than a cooldown period ago.
///
/// Maven metadata carries no per-version dates, so the publish time of a version is taken from the `Last-Modified`
/// header of its POM, fetched with a `HEAD` request against the repository the version was found in. For `file:`
/// repositories the POM's modification time is used instead.
///
/// A version whose publish time cannot be determined is treated as too new, so the cooldown fails closed.
///
/// Note that a proxying repository manager may report when it cached the POM rather than when it was published
/// upstream, which makes versions appear newer than they are.
final class ReleaseCooldown {
    private static final ReleaseCooldown DISABLED =
            new ReleaseCooldown(Duration.ZERO, Clock.systemUTC(), null, s -> {}, s -> {});

    private final Duration cooldown;
    private final Clock clock;
    private final @Nullable HttpClient httpClient;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;
    private final ConcurrentMap<URI, Option<Instant>> publishedCache = new ConcurrentHashMap<>();

    ReleaseCooldown(
            Duration cooldown,
            Clock clock,
            @Nullable HttpClient httpClient,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger) {
        this.cooldown = cooldown;
        this.clock = clock;
        this.httpClient = httpClient;
        this.infoLogger = infoLogger;
        this.warningLogger = warningLogger;
    }

    static ReleaseCooldown disabled() {
        return DISABLED;
    }

    static ReleaseCooldown ofDays(int days, Consumer<String> infoLogger, Consumer<String> warningLogger) {
        if (days <= 0) {
            return DISABLED;
        }
        // TODO: honour the session's ProxySelector; java.net.http only supports a proxy per client.
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        return new ReleaseCooldown(Duration.ofDays(days), Clock.systemUTC(), client, infoLogger, warningLogger);
    }

    boolean isEnabled() {
        return !cooldown.isZero() && !cooldown.isNegative();
    }

    /**
     * Drops versions from the top of the list until the newest remaining one is outside the cooldown.
     *
     * <p>Only the top of the list is checked, so a version that sorts lower but was published more recently (such as a
     * backport release) is not held back. This keeps the cost to one request per version dropped, plus one.
     *
     * @param session the session, supplying credentials for the repositories.
     * @param rangeResult the range resolution the versions came from, giving the repository of each version.
     * @param resolve the request being resolved.
     * @param versions the candidate versions, oldest first.
     * @return the versions that remain, oldest first.
     */
    List<Version> apply(
            RepositorySystemSession session, VersionRangeResult rangeResult, Resolve resolve, List<Version> versions) {
        if (!isEnabled()) {
            return versions;
        }
        Instant cutoff = clock.instant().minus(cooldown);
        List<Version> remaining = versions;
        while (!remaining.isEmpty()) {
            Version newest = remaining.last();
            Option<Instant> published = publishedAt(session, rangeResult.getRepository(newest), resolve, newest);
            if (published.isDefined() && !published.get().isAfter(cutoff)) {
                return remaining;
            }
            infoLogger.accept("Holding back %s:%s, %s"
                    .formatted(
                            resolve.key(),
                            newest,
                            published
                                    .map(p ->
                                            "published %s, within the %d day cooldown".formatted(p, cooldown.toDays()))
                                    .getOrElse("publish date unknown")));
            remaining = remaining.init();
        }
        return remaining;
    }

    private Option<Instant> publishedAt(
            RepositorySystemSession session,
            @Nullable ArtifactRepository repository,
            Resolve resolve,
            Version version) {
        if (!(repository instanceof RemoteRepository remote)) {
            // Found only in the local repository or workspace, so there is nowhere to ask.
            return none();
        }
        URI pom = pomUri(remote, resolve, version);
        return publishedCache.computeIfAbsent(pom, uri -> fetchPublished(session, remote, uri));
    }

    static URI pomUri(RemoteRepository repository, Resolve resolve, Version version) {
        String base = repository.getUrl().endsWith("/") ? repository.getUrl() : repository.getUrl() + "/";
        String path = "%s/%s/%s/%s-%s.pom"
                .formatted(resolve.group().replace('.', '/'), resolve.artifact(), version, resolve.artifact(), version);
        return URI.create(base).resolve(path);
    }

    private Option<Instant> fetchPublished(RepositorySystemSession session, RemoteRepository repository, URI pom) {
        if ("file".equals(pom.getScheme())) {
            try {
                return some(Files.getLastModifiedTime(Path.of(pom)).toInstant());
            } catch (IOException e) {
                warningLogger.accept("Unable to read the publish date of %s: %s".formatted(pom, e.getMessage()));
                return none();
            }
        }
        if (httpClient == null) {
            return none();
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(pom)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30));
        basicAuthorization(session, repository).forEach(header -> request.header("Authorization", header));

        try {
            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                warningLogger.accept(
                        "Unable to read the publish date of %s: HTTP %d".formatted(pom, response.statusCode()));
                return none();
            }
            return Option.ofOptional(response.headers().firstValue("Last-Modified"))
                    .map(value -> ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant());
        } catch (IOException | DateTimeParseException e) {
            warningLogger.accept("Unable to read the publish date of %s: %s".formatted(pom, e.getMessage()));
            return none();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return none();
        }
    }

    /**
     * Builds a basic {@code Authorization} header from the repository's credentials, falling back to the session's
     * authentication selector (the {@code <server>} entries in {@code settings.xml}).
     */
    private static Option<String> basicAuthorization(RepositorySystemSession session, RemoteRepository repository) {
        RemoteRepository authenticated = repository.getAuthentication() != null
                ? repository
                : new RemoteRepository.Builder(repository)
                        .setAuthentication(session.getAuthenticationSelector().getAuthentication(repository))
                        .build();
        try (AuthenticationContext context = AuthenticationContext.forRepository(session, authenticated)) {
            if (context == null) {
                return none();
            }
            String username = context.get(AuthenticationContext.USERNAME);
            String secret = context.get(AuthenticationContext.PASSWORD);
            if (username == null || secret == null) {
                return none();
            }
            String token =
                    Base64.getEncoder().encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8));
            return some("Basic " + token);
        }
    }
}
