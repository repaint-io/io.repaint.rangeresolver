---
title: Using the Java API
description: Parse, resolve and write Range Resolver scripts from Java.
---

The library has three entry points, all in `io.repaint.rangeresolver.lang`:

| Class | Responsibility |
| --- | --- |
| `RangeResolverProcessor` | Parses script text into an immutable `RangeResolver` model. |
| `RepositoryResolver` | Resolves a model's requests against Maven repositories. |
| `RepositoryWriter` | Writes a model back out as script text. |

The model types (`RangeResolver`, `Resolve`, `Resolved`, `Repository`, `Import`, `Alias`, `Allow`, `Range` and so on)
live in `io.repaint.rangeresolver.api`. They are [Immutables](https://immutables.github.io/) value classes, so every
change returns a new copy, for example `withLocked(...)`.

## Parsing

```java
import io.repaint.rangeresolver.api.RangeResolver;
import static io.repaint.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;

RangeResolver model;
try (var reader = Files.newBufferedReader(Path.of("pom.deps"))) {
  model = parseRangeResolver(
      "pom.deps", // source reference, used in error messages
      reader,
      "",         // allowUnlocked: extra pattern of keys to unlock in imports ("" for none)
      "");        // allowSnapshots: extra pattern of keys allowed to resolve snapshots ("" for none)
}
```

A script with any lexer or parser error throws `RangeResolverSyntaxException`, whose message names the source,
line and column:

```text
Syntax error in pom.deps: line 2:0 mismatched input 'resolve' expecting {'filtering', 'via', ';'}
```

The two pattern arguments add non-persistent `allow unlocked` and `allow snapshots` rules on top of the ones in the
script. The Maven plugin uses them for its `-DallowUnlocked=…` and `-DallowSnapshots=…` options. They are never
written back to the file.

## Resolving

```java
import io.repaint.rangeresolver.api.ResolutionResult;
import io.repaint.rangeresolver.lang.RepositoryResolver;

RepositoryResolver resolver = RepositoryResolver.createRepositoryResolver(
    System.out::println,                   // info logger
    System.err::println,                   // warning logger
    System.getProperty("user.home") + "/.m2/repository",
    "",                                    // allowUnlocked
    "",                                    // allowSnapshots
    false);                                // updateSnapshots: check remotes every time, not daily

ResolutionResult result = resolver.resolveRanges(model, "my-project", "pom.deps");

result.resolved().forEach(r -> System.out.println(r.key() + " -> " + r.version()));
```

Inside a Maven plugin or extension, pass the build's own `RepositorySystem` and `RepositorySystemSession` to the
other `createRepositoryResolver` overload. Resolution then honours the user's `settings.xml` mirrors, proxies and
credentials.

Each repository uses the [release cooldown](../../language/repositories/#release-cooldown) declared on it. To use one
cooldown for every repository instead, call `withCooldownDays`. This also replaces any
[cooldown declared on a request](../../language/resolution/#cooldown). It returns a new resolver and leaves the
original unchanged:

```java
RepositoryResolver urgent = resolver.withCooldownDays(0);
```

## Writing

To produce an updated script, copy the result lists onto the model and write it out:

```java
import static io.repaint.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

RangeResolver updated = model
    .withResolved(result.resolved())
    .withLocked(result.locked())
    .withDeprecated(result.deprecated())
    .withBlacklisted(result.blacklisted());

writeRangeResolveSource(updated, Files.newBufferedWriter(Path.of("pom.deps")));
```

The writer emits statements in a fixed order (repositories, imports, allows, withholds, blacklists, deprecations,
Docker resolutions, resolutions, overrides, aliases, then the records), separates the sections with blank lines, and
keeps each statement's leading comments.

## Helpers

`RepositoryResolver` also exposes the static helpers the plugin goals are built on:

- `unlockDependencies(model, patterns)` moves matching `locked` records back to `resolved`.
- `snapshotDependencies(model, logger, patterns)` switches matching requests to the `snapshot` bound.
- `isNewerVersion(a, b)` compares two versions using Maven ordering.
