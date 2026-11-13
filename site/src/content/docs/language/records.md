---
title: Resolved records
description: resolved, locked, deprecated and blacklisted, the resolver's output.
---

```text
resolved    <group>:<artifact>:<version>;
locked      <group>:<artifact>:<version>;
deprecated  <group>:<artifact>:<version>;
blacklisted <group>:<artifact>:<version>;

resolved    <image>@<algorithm>:<digest>;   (and so on for Docker images)
```

Records hold exact versions. The resolver writes them, and you rarely edit them by hand, except to delete one.

## `resolved` and `locked`

Both record the version a build should use. They differ in where they are in the workflow:

| Record | Meaning |
| --- | --- |
| `resolved` | Chosen by the most recent resolution run and awaiting review. |
| `locked` | Accepted and pinned. |

A typical cycle with the Maven plugin:

1. **`resolve-deps`** resolves every request. A version newer than the current lock is written as a `resolved`
   record, and the matching `locked` record is dropped.
2. You review the diff and build and test.
3. **`lock-deps`** moves every `resolved` record into `locked`.

**`unlock-deps`** does the reverse and moves every `locked` record back to `resolved`. The next `resolve-deps` then
no longer treats those versions as a minimum. See [Using with Apache Maven](../../guide/maven/).

```diff lang="rangeresolver"
-locked com.google.guava:guava:33.8.0-jre;
+resolved com.google.guava:guava:33.9.0-jre;
```

## `deprecated` and `blacklisted`

These record the versions picked by local [`deprecate` and `blacklist`](../resolution/#deprecate-and-blacklist)
requests, together with those inherited from imports. They flag versions to avoid. They don't select a version to
use.

## Versions only move forward

A locked, deprecated or blacklisted record is the floor for its artifact: a later run replaces it only with a strictly
newer version. To go back to an older version, delete the record and resolve again.

## Ordering

When the file is rewritten, records are grouped in the order `blacklisted`, `deprecated`, `resolved`, `locked`.
Within each group they are sorted by key, with Maven artifacts and Docker images in separate blocks.
