package io.repaint.rangeresolver.lang;

import dev.zazr.control.Option;

import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Repository;
import io.repaint.rangeresolver.api.Resolve;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.time.Duration;
import java.util.function.Function;

import static dev.zazr.control.Option.none;
import static dev.zazr.control.Option.some;

import static io.repaint.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static io.repaint.rangeresolver.lang.RepositoryResolver.cooldowns;
import static io.repaint.rangeresolver.lang.RepositoryResolver.cooldownsFor;
import static io.repaint.rangeresolver.lang.RepositoryResolver.isNewerVersion;
import static io.repaint.rangeresolver.lang.RepositoryResolver.repositoryId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RepositoryResolverTest {
    @Test
    public void versionComparision() {
        assertThat(isNewerVersion("1.0.1", "1.0.2")).isFalse();
        assertThat(isNewerVersion("1.0.1", "1.0.1")).isFalse();
        assertThat(isNewerVersion("1.0.2", "1.0.1")).isTrue();
        assertThat(isNewerVersion("2.0.2", "1.0.1")).isTrue();
        assertThat(isNewerVersion("23.2-jre", "22.0")).isTrue();
    }

    @Test
    public void repositoryIdUsesNameWhenDeclared() {
        Repository central = Repository.of("", "https://repo1.maven.org/maven2", "default", 0);

        assertThat(repositoryId(central)).isEqualTo("https://repo1.maven.org/maven2");
    }

    @Test
    public void cooldownsComeFromEachRepository() throws Exception {
        RangeResolver rangeResolver = parseRangeResolver("test", Reader.of("""
                        repository https://repo1.maven.org/maven2 cooldown 7;
                        repository https://repo.example.com/releases as internal;
                        repository https://repo.example.com/staging as staging cooldown 3;
                        """), "", "");
        Function<RemoteRepository, Duration> cooldowns = cooldowns(rangeResolver, none());

        RemoteRepository central = remote("https://repo1.maven.org/maven2");
        RemoteRepository internal = remote("internal");
        RemoteRepository staging = remote("staging");
        RemoteRepository mirror = new RemoteRepository.Builder("mirror", "default", "https://mirror.example.com")
                .setMirroredRepositories(java.util.List.of(internal, staging))
                .build();

        assertThat(cooldowns.apply(central)).isEqualTo(Duration.ofDays(7));
        assertThat(cooldowns.apply(internal)).isEqualTo(Duration.ZERO);
        assertThat(cooldowns.apply(staging)).isEqualTo(Duration.ofDays(3));
        assertThat(cooldowns.apply(mirror)).isEqualTo(Duration.ofDays(3));
        assertThat(cooldowns.apply(remote("undeclared"))).isEqualTo(Duration.ZERO);
    }

    @Test
    public void cooldownOverrideAppliesToEveryRepository() throws Exception {
        RangeResolver rangeResolver = parseRangeResolver("test", Reader.of("""
                        repository https://repo1.maven.org/maven2 cooldown 7;
                        repository https://repo.example.com/releases as internal;
                        """), "", "");

        Function<RemoteRepository, Duration> disabled = cooldowns(rangeResolver, some(Duration.ZERO));
        assertThat(disabled.apply(remote("https://repo1.maven.org/maven2"))).isEqualTo(Duration.ZERO);

        Function<RemoteRepository, Duration> stricter = cooldowns(rangeResolver, some(Duration.ofDays(14)));
        assertThat(stricter.apply(remote("https://repo1.maven.org/maven2"))).isEqualTo(Duration.ofDays(14));
        assertThat(stricter.apply(remote("internal"))).isEqualTo(Duration.ofDays(14));
    }

    @Test
    public void statementCooldownsReplaceRepositoryCooldowns() throws Exception {
        RangeResolver rangeResolver = parseRangeResolver("test", Reader.of("""
                        repository https://repo1.maven.org/maven2 cooldown 7;
                        repository https://repo.example.com/releases as internal;
                        resolve highest com.example:longer:[1,2) cooldown 30;
                        resolve highest com.example:exempt:[1,2) cooldown 0;
                        deprecate highest com.example:inherits:[1,2);
                        """), "", "");
        Function<RemoteRepository, Duration> repositoryCooldowns = cooldowns(rangeResolver, none());
        RemoteRepository central = remote("https://repo1.maven.org/maven2");
        RemoteRepository internal = remote("internal");

        Resolve longer = resolve(rangeResolver, "com.example:longer");
        Resolve exempt = resolve(rangeResolver, "com.example:exempt");
        Resolve inherits = resolve(rangeResolver, "com.example:inherits");

        assertThat(cooldownsFor(longer, repositoryCooldowns, none()).apply(central))
                .isEqualTo(Duration.ofDays(30));
        assertThat(cooldownsFor(longer, repositoryCooldowns, none()).apply(internal))
                .isEqualTo(Duration.ofDays(30));
        assertThat(cooldownsFor(exempt, repositoryCooldowns, none()).apply(central))
                .isEqualTo(Duration.ZERO);
        assertThat(cooldownsFor(inherits, repositoryCooldowns, none()).apply(central))
                .isEqualTo(Duration.ofDays(7));
        assertThat(cooldownsFor(inherits, repositoryCooldowns, none()).apply(internal))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    public void cooldownOverrideAppliesOverStatementCooldowns() throws Exception {
        RangeResolver rangeResolver = parseRangeResolver("test", Reader.of("""
                        repository https://repo1.maven.org/maven2 cooldown 7;
                        resolve highest com.example:longer:[1,2) cooldown 30;
                        """), "", "");
        Option<Duration> override = some(Duration.ZERO);
        Function<RemoteRepository, Duration> repositoryCooldowns = cooldowns(rangeResolver, override);

        assertThat(cooldownsFor(resolve(rangeResolver, "com.example:longer"), repositoryCooldowns, override)
                        .apply(remote("https://repo1.maven.org/maven2")))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    public void invalidCooldownsAreRejected() {
        assertThatThrownBy(() -> parseRangeResolver(
                        "test", Reader.of("resolve highest com.example:widget:[1,2) cooldown -3;"), "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid cooldown '-3' at line 1 in test, expected a whole number of days");
        assertThatThrownBy(() -> parseRangeResolver(
                        "test", Reader.of("repository https://repo1.maven.org/maven2 cooldown week;"), "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid cooldown 'week' at line 1 in test, expected a whole number of days");
    }

    private static Resolve resolve(RangeResolver rangeResolver, String key) {
        return rangeResolver.resolves().find(r -> r.key().equals(key)).get();
    }

    private static RemoteRepository remote(String id) {
        return new RemoteRepository.Builder(id, "default", "https://example.com/" + id).build();
    }
}
