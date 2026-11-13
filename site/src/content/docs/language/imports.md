---
title: Imports & policies
description: Sharing locked versions between projects with import, allow and withhold.
---

## `import`

```text
import <group>:<artifact>:<version>;
```

An `import` pulls in another project's script as a **bill of materials**. The resolver downloads:

```text
<group>:<artifact>:<version>:bill-of-materials@deps
```

That is the artifact with classifier `bill-of-materials` and extension `deps`. The Maven plugin's `attach-deps`
goal publishes a project's `pom.deps` under these coordinates.

```rangeresolver
repository https://repo.example.com/releases/;

import com.example:platform:4.2.0;
import com.example:observability:1.8.3;
```

From the imported scripts:

- Their `locked`, `resolved`, `deprecated` and `blacklisted` records become the **baseline** versions for your
  project.
- Their `resolve` requests are **not** re-resolved, unless you allow it with `allow unlocked` or `override`.

So, by default, an import means "use exactly what that project locked". Importing the same coordinates twice is an
error.

An import version ending in `-SNAPSHOT` is fetched with snapshots allowed.

## `allow unlocked`

```text
allow unlocked /<regex>/;
```

Lets imported requests whose `group:artifact` key matches the pattern be re-resolved locally. If the resolver finds
a version newer than the imported baseline, it records it as `resolved` in your script.

```rangeresolver
import com.example:platform:4.2.0;

// Re-resolve our own artifacts. Leave third-party versions exactly as the platform locked them.
allow unlocked /^com\.example:.*$/;
```

## `allow snapshots`

```text
allow snapshots /<regex>/;
```

Switches imported requests whose key matches the pattern to the [`snapshot` bound](../resolution/#bounds), so they
may resolve to `-SNAPSHOT` versions. Each switch is logged as a warning (`Allowing SNAPSHOT resolution of …`).
An `allow snapshots` pattern also counts as an `allow` when deciding which imported requests are re-resolved.

```rangeresolver
allow unlocked  /^com\.example:widgets$/;
allow snapshots /^com\.example:widgets$/;
```

Both kinds of `allow` can also come from outside the script: the Maven plugin's `-DallowUnlocked=<regex>` and
`-DallowSnapshots=<regex>` options add the same rules for one run, without writing them to the file.

## `withhold`

```text
withhold /<regex>/;
```

Excludes matching keys from re-resolution even when an `allow` pattern matches them. Use it to carve exceptions out
of a broad `allow`:

```rangeresolver
allow unlocked /^com\.example:.*$/;

// The payments client is certified separately and must stay at the platform's version.
withhold /^com\.example:payments-client$/;
```

A local [`override`](../resolution/#override) still wins over `withhold`.

## Writing patterns

- The pattern must match the whole `group:artifact` key, so `/com\.example/` matches nothing. Use
  `/^com\.example:.*$/` or `/com\.example:.*/`.
- Matching ignores case.
- Escape the dots if they matter: an unescaped `.` matches any character.
