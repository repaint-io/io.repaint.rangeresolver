package io.repaint.rangeresolver.lang;

import com.google.common.base.Suppliers;

import dev.zazr.collection.List;
import dev.zazr.control.Option;

import io.repaint.rangeresolver.api.Resolve;

import org.eclipse.aether.RepositorySystemSession;
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
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.zazr.control.Option.none;
import static dev.zazr.control.Option.some;

/// Holds back versions published less than a cooldown period ago.
///
/// Each repository carries its own cooldown, declared with `repository <url> cooldown <days>;`. A version is held back
/// using the cooldown of the repository it was found in, so a trusted internal repository can have no cooldown while
/// a public one does.
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
    private final Clock clock;
    private final Supplier<@Nullable HttpClient> httpClient;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;
    private final ConcurrentMap<URI, Option<Instant>> publishedCache = new ConcurrentHashMap<>();

    ReleaseCooldown(
            Clock clock,
            Supplier<@Nullable HttpClient> httpClient,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger) {
        this.clock = clock;
        this.httpClient = httpClient;
        this.infoLogger = infoLogger;
        this.warningLogger = warningLogger;
    }

    /// Creates a cooldown that checks publish dates over HTTP. The HTTP client is only created once a repository with a
    /// cooldown is queried.
    static ReleaseCooldown create(Consumer<String> infoLogger, Consumer<String> warningLogger) {
        // TODO: honour the session's ProxySelector; java.net.http only supports a proxy per client.
        Supplier<@Nullable HttpClient> client = Suppliers.memoize(() -> HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build());
        return new ReleaseCooldown(Clock.systemUTC(), client, infoLogger, warningLogger);
    }

    /**
     * Drops versions from the top of the list until the newest remaining one is outside the cooldown of the repository
     * it was found in.
     *
     * <p>Only the top of the list is checked, so a version that sorts lower but was published more recently (such as a
     * backport release) is not held back. This keeps the cost to one request per version dropped, plus one.
     *
     * @param session the session, supplying credentials for the repositories.
     * @param rangeResult the range resolution the versions came from, giving the repository of each version.
     * @param resolve the request being resolved.
     * @param versions the candidate versions, oldest first.
     * @param cooldownOf the cooldown of each repository, {@link Duration#ZERO} for none.
     * @return the versions that remain, oldest first.
     */
    List<Version> apply(
            RepositorySystemSession session,
            VersionRangeResult rangeResult,
            Resolve resolve,
            List<Version> versions,
            Function<RemoteRepository, Duration> cooldownOf) {
        List<RemoteRepository> repositories =
                List.ofAll(rangeResult.getRequest().getRepositories());
        if (!repositories.exists(repository -> isActive(cooldownOf.apply(repository)))) {
            return versions;
        }
        Instant now = clock.instant();
        List<Version> remaining = versions;
        while (!remaining.isEmpty()) {
            Version newest = remaining.last();
            Publication publication = publication(session, rangeResult, repositories, resolve, newest, cooldownOf);
            if (!publication.isHeldBack(now)) {
                return remaining;
            }
            infoLogger.accept("Holding back %s:%s, %s".formatted(resolve.key(), newest, publication.reason()));
            remaining = remaining.init();
        }
        return remaining;
    }

    private static boolean isActive(Duration cooldown) {
        return !cooldown.isZero() && !cooldown.isNegative();
    }

    /// When a version was published, and the cooldown that applies to it.
    ///
    /// @param cooldown the cooldown of the repository the version was found in.
    /// @param at the publish time, empty if it is unknown or was not needed because there is no cooldown.
    private record Publication(Duration cooldown, Option<Instant> at) {
        boolean isHeldBack(Instant now) {
            return isActive(cooldown)
                    && at.map(p -> p.isAfter(now.minus(cooldown))).getOrElse(true);
        }

        /// Describes why the version is held back, for logging.
        String reason() {
            var cooldownDays = cooldown.toDays();
            return at.map(p -> "published %s, within the %d day cooldown".formatted(p, cooldownDays))
                    .getOrElse("publish date unknown");
        }
    }

    /**
     * Finds the publish time of a version from the remote repository it was found in.
     *
     * <p>A version listed in the local repository's own metadata ({@code maven-metadata-local.xml}, written by a local
     * install) is attributed to the local repository even when it was also published remotely, so for those the
     * request's remote repositories are asked in order instead, and the cooldown of the first one that has it applies.
     * If none has it, the longest cooldown of the request's repositories applies.
     */
    private Publication publication(
            RepositorySystemSession session,
            VersionRangeResult rangeResult,
            List<RemoteRepository> repositories,
            Resolve resolve,
            Version version,
            Function<RemoteRepository, Duration> cooldownOf) {
        if (rangeResult.getRepository(version) instanceof RemoteRepository remote) {
            Duration cooldown = cooldownOf.apply(remote);
            return isActive(cooldown)
                    ? new Publication(cooldown, publishedAt(session, remote, resolve, version))
                    : new Publication(cooldown, none());
        }
        for (RemoteRepository remote : repositories) {
            Option<Instant> published = publishedAt(session, remote, resolve, version);
            if (published.isDefined()) {
                return new Publication(cooldownOf.apply(remote), published);
            }
        }
        Duration longest = repositories.map(cooldownOf).max().getOrElse(Duration.ZERO);
        return new Publication(longest, none());
    }

    private Option<Instant> publishedAt(
            RepositorySystemSession session, RemoteRepository remote, Resolve resolve, Version version) {
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
        HttpClient client = httpClient.get();
        if (client == null) {
            return none();
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(pom)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30));
        basicAuthorization(session, repository).forEach(header -> request.header("Authorization", header));

        try {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
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
