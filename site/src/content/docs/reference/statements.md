---
title: Statement reference
description: Every statement in the language on one page.
---

Square brackets mark optional parts. `<regex>` is always written between slashes.

## Configuration

| Statement | Syntax | Notes |
| --- | --- | --- |
| repository | `repository <url> [as <name>];` | `<name>` is the grouping and the repository id. It defaults to the `default` grouping, with the URL as the id. [More](../../language/repositories/) |
| import | `import <group>:<artifact>:<version>;` | Fetches `<group>:<artifact>:<version>:bill-of-materials@deps`. Duplicates are an error. [More](../../language/imports/) |
| allow unlocked | `allow unlocked /<regex>/;` | Imported requests with matching keys may be re-resolved. [More](../../language/imports/#allow-unlocked) |
| allow snapshots | `allow snapshots /<regex>/;` | Imported requests with matching keys use the `snapshot` bound. [More](../../language/imports/#allow-snapshots) |
| withhold | `withhold /<regex>/;` | Matching keys are never re-resolved through an `allow`. [More](../../language/imports/#withhold) |
| alias | `alias <group>:<artifact> as <property>;` | Overrides the default `<artifactId>.version` property name. [More](../../language/aliases/) |

## Requests

All Maven requests share this form:

```text
<verb> <bound> <group>:<artifact>:<range> [via <grouping>] [filtering /<regex>/];
```

| Verb | Produces | Notes |
| --- | --- | --- |
| `resolve` | `resolved` | [More](../../language/resolution/) |
| `override` | `resolved` | Ignores `allow` and `withhold` for imported requests. [More](../../language/resolution/#override) |
| `deprecate` | `deprecated` | [More](../../language/resolution/#deprecate-and-blacklist) |
| `blacklist` | `blacklisted` | [More](../../language/resolution/#deprecate-and-blacklist) |

| Bound | Selects | Default filter (excluded versions) |
| --- | --- | --- |
| `lowest` | Oldest matching version. | `^.*-SNAPSHOT$` |
| `highest` | Newest matching version. | `^.*-SNAPSHOT$` |
| `snapshot` | Newest matching version. | None |

Docker requests have their own form:

```text
resolve docker [<registry>[:<port>]/]<image>:<tag>;
```

[More](../../language/docker/)

## Records

```text
resolved    <group>:<artifact>:<version>;     resolved    <image>@<algo>:<digest>;
locked      <group>:<artifact>:<version>;     locked      <image>@<algo>:<digest>;
deprecated  <group>:<artifact>:<version>;     deprecated  <image>@<algo>:<digest>;
blacklisted <group>:<artifact>:<version>;     blacklisted <image>@<algo>:<digest>;
```

[More](../../language/records/)

## Ranges

| Form | Example |
| --- | --- |
| Inclusive and exclusive bounds | `[1.0,2.0)`, `(1.0,2.0]` |
| Half-open | `[1.0,)`, `(,2.0]` |
| Exact | `[1.2.3]` or `1.2.3` |

[More](../../language/ranges/)

## Keywords

None of these words is reserved. Each can also appear as a group id, artifact id, version or name.

`alias` `allow` `as` `blacklist` `blacklisted` `deprecate` `deprecated` `docker` `filtering` `highest` `import`
`locked` `lowest` `override` `repository` `resolve` `resolved` `snapshot` `snapshots` `unlocked` `via` `withhold`
