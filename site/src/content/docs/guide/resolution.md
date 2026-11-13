---
title: How resolution works
description: The rules the resolver follows when it turns requests into versions.
---

A resolution run takes a parsed script and produces four sorted lists of records: `resolved`, `locked`,
`deprecated` and `blacklisted`. This page describes how each list is built.

## 1. Load imports

Each `import group:artifact:version;` is fetched from the declared repositories as the artifact
`group:artifact:version`, classifier `bill-of-materials`, extension `deps`. That file is itself a Range Resolver
script, and it is parsed with the same rules.

Every `locked`, `resolved`, `deprecated` and `blacklisted` record in an imported script becomes a **baseline
version** for its artifact.

An import whose version ends in `-SNAPSHOT` is looked up with snapshots allowed. Any other import ignores snapshots.

## 2. Decide what to resolve

The resolver builds the set of requests to resolve:

- **Every request declared locally**: each `resolve`, `override`, `deprecate`, `blacklist` and `resolve docker`
  statement in this script.
- **Imported requests you are allowed to move**: a `resolve` from an imported script is included only if its
  `group:artifact` key:
  - matches an `allow` pattern and no `withhold` pattern, or
  - is the subject of a local `override`.

Imported requests whose keys match an `allow snapshots` pattern switch to the `snapshot` bound.

## 3. Pick a version for each request

For each request the resolver:

1. Asks the repositories in the request's grouping (`via name`, or the unnamed default repositories) for every
   version inside the range.
2. Removes versions that **fully match** the filter pattern. Matching ignores case. With no `filtering` clause the
   filter is `^.*-SNAPSHOT$`, except for the `snapshot` bound, which filters nothing.
3. Takes the lowest remaining version for `lowest`, or the highest for `highest` and `snapshot`.

If no version survives, the resolver logs a warning naming the range, the repositories and the source script, and
produces nothing for that request.

## 4. Only move forward

A picked version is kept only if it is **strictly newer**, by Maven version ordering, than:

- the baseline version from any import, and
- any `locked`, `deprecated` or `blacklisted` record for the same artifact in the local script.

Local `resolve` and `override` requests skip the import comparison, but not the local one. As a result, a locked
version never goes backwards. To downgrade, delete the `locked` line and resolve again.

`deprecate` and `blacklist` requests always produce a record. They describe a range of versions you want flagged,
not a version to use.

## 5. Assemble the result

| List | Contents |
| --- | --- |
| `resolved` | Versions picked in this run by `resolve` and `override` requests. |
| `deprecated` | Imported `deprecated` records, plus the versions picked by local `deprecate` requests. |
| `blacklisted` | Imported `blacklisted` records, plus the versions picked by local `blacklist` requests. |
| `locked` | Baseline versions from imports and local `locked` records (the newer of the two when both exist), minus any artifact already in one of the lists above. |

A local `locked` record for an artifact that has a local `override` isn't carried into the `locked` list. The
override's result, or failing that the import's baseline, takes its place.

## Consistency checks

Parsing fails with an `IllegalStateException` if:

- the same `import` appears twice, or
- the same artifact has two `alias` statements.

Resolution fails if an imported request is resolved locally and is also deprecated or blacklisted locally.

If several sources resolve the same artifact, the resolver logs a `Duplicate resolution of …` warning that lists
them.
