---
title: Resolution requests
description: resolve, override, deprecate and blacklist.
---

```text
resolve   <bound> <group>:<artifact>:<range> [via <grouping>] [filtering /<regex>/] [cooldown <days>];
override  <bound> <group>:<artifact>:<range> [via <grouping>] [filtering /<regex>/] [cooldown <days>];
deprecate <bound> <group>:<artifact>:<range> [via <grouping>] [filtering /<regex>/] [cooldown <days>];
blacklist <bound> <group>:<artifact>:<range> [via <grouping>] [filtering /<regex>/] [cooldown <days>];
```

All four request statements share one form. They differ in what happens to the version they pick.

## Bounds

The bound chooses one version from the versions the repository has in the range.

| Bound | Picks | Default filter |
| --- | --- | --- |
| `lowest` | The oldest matching version. | Excludes `-SNAPSHOT` versions. |
| `highest` | The newest matching version. | Excludes `-SNAPSHOT` versions. |
| `snapshot` | The newest matching version, snapshots included. | Excludes nothing. |

```rangeresolver
// A library: compile against the oldest API we support.
resolve lowest org.slf4j:slf4j-api:[2.0.0,3.0.0);

// An application: take the newest compatible release.
resolve highest com.google.guava:guava:[33.0.0-jre,34.0.0);

// Track a sibling project's in-progress development line.
resolve snapshot com.example:widgets:[2.0.0,3.0.0);
```

## Ranges

Ranges use Maven's version range syntax. A bare version such as `1.2.3` means exactly that version. See
[Version ranges](../ranges/) for the full syntax.

## `via`

`via <grouping>` limits the search to the repositories declared `as <grouping>`. Without `via`, the request searches
the unnamed (`default`) repositories. See [Repositories](../repositories/#groupings).

## `filtering`

`filtering /regex/` replaces the default filter. Versions that **match** the pattern are **excluded**. The pattern
must match the whole version string and ignores case.

```rangeresolver
// Skip pre-releases: 3.0-rc5, 2.0.0-beta1, 1.0-SNAPSHOT, ...
resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.7.0,3.0.0] filtering /^.*(ea|alpha|beta|rc|SNAPSHOT).*$/;
```

:::caution
A `filtering` clause replaces the default snapshot filter rather than adding to it. If you still want to skip
snapshots, include `SNAPSHOT` in your pattern.
:::

A pattern with no metacharacters is still a regular expression. `/release/` excludes only the version that is
exactly `release`.

## `cooldown`

`cooldown <days>` holds back versions of this artifact published within that many days. It replaces the
[release cooldown](../repositories/#release-cooldown) of every repository the versions come from, so it can lengthen
the cooldown for one artifact or, with `cooldown 0`, exempt a trusted one:

```rangeresolver
repository https://repo1.maven.org/maven2/ cooldown 7;

// A risky dependency waits a month.
resolve highest com.example:fragile:[1.0,2.0) cooldown 30;

// Our own library is picked up the day it ships.
resolve highest com.example:widgets:[2.0,3.0) cooldown 0;

// Everything else uses the repository's 7 days.
resolve highest org.slf4j:slf4j-api:[2.0.0,3.0.0);
```

A request without a `cooldown` clause uses the cooldown of the repository each version was found in. A request
read from an [imported](../imports/) script keeps the cooldown declared there. The Maven plugin's `cooldownDays`
parameter, or `withCooldownDays` in the Java API, still [overrides](../repositories/#overriding-the-cooldown) every
cooldown, including these.

## Properties

Each resolved version is published as the property `<artifactId>.version`. For example,
`com.google.guava:guava` becomes `guava.version`. Use an [`alias`](../aliases/) to choose a different name.

## `override`

`override` works like `resolve`, with two differences:

- It re-resolves an **imported** request even when no `allow unlocked` pattern matches it, or a `withhold` pattern
  does.
- The version it picks only has to be newer than your own locked version, not the import's baseline. A local
  `locked` record for the artifact isn't carried forward unchanged.

Use it when a project must diverge from a bill of materials it imports:

```rangeresolver
import com.example:platform-bom:4.2.0;

// The platform pins Netty 4.1; this service needs 4.2.
override highest io.netty:netty-all:[4.2.0,4.3.0);
```

## `deprecate` and `blacklist`

`deprecate` and `blacklist` pick a version exactly as `resolve` does. The result is recorded as `deprecated` or
`blacklisted` rather than `resolved`:

```rangeresolver
deprecate highest com.example:legacy-client:[1.0.0,2.0.0);
blacklist highest org.apache.logging.log4j:log4j-core:[2.0,2.17.0);
```

After resolution:

```rangeresolver
deprecated com.example:legacy-client:1.9.4;
blacklisted org.apache.logging.log4j:log4j-core:2.16.0;
```

These records travel with the script. Projects that [import](../imports/) it inherit them, so tooling such as the
Maven plugin's `verify` goal can warn about or reject builds that still depend on those artifacts. Its
`allowDeprecatedDependencies` and `allowBlacklistedDependencies` options control which.

An artifact deprecated by a local `deprecate` request is never also recorded as `resolved` in the same run.
