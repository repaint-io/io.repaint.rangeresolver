---
title: Version ranges
description: The version range syntax used by resolution requests.
---

Ranges follow [Maven's version range
syntax](https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html). `[` and `]` include their bound, and
`(` and `)` exclude it.

| Range | Meaning |
| --- | --- |
| `[1.0.0,2.0.0)` | At least 1.0.0 and below 2.0.0. |
| `[1.0.0,2.0.0]` | 1.0.0 to 2.0.0, both included. |
| `(1.0.0,2.0.0)` | Strictly between 1.0.0 and 2.0.0. |
| `[1.0.0,)` | 1.0.0 or later. |
| `(,2.0.0]` | Up to and including 2.0.0. |
| `[1.2.3]` | Exactly 1.2.3. |
| `1.2.3` | Exactly 1.2.3. |

```rangeresolver
resolve highest org.slf4j:slf4j-api:[2.0.0,3.0.0);
resolve lowest  org.slf4j:slf4j-simple:[2.0.0,);
resolve highest org.slf4j:jul-to-slf4j:(,2.0.9];
resolve highest com.example:pinned:1.4.2;
```

:::note
In a Maven POM, a bare version such as `1.4.2` is only a "soft" preference. In Range Resolver it is a hard
requirement, the same as `[1.4.2]`. The writer always prints it in the bracketed form.
:::

## Ordering

Versions are compared with Maven's `ComparableVersion` rules, so `1.10` is newer than `1.9`, and qualifiers such as
`-alpha`, `-rc1` and `-SNAPSHOT` sort before the release they qualify.

A range whose lower bound is newer than its upper bound, such as `[2.0,1.0]`, is rejected with
`Invalid range requested`.

## Pre-releases inside ranges

`[2.7.0,3.0.0]` contains `3.0-rc5`, because `3.0-rc5` sorts before `3.0.0`. With `highest`, that release candidate
may be picked. Add a [`filtering`](../resolution/#filtering) clause to exclude pre-releases, or end the range
before the next major line with an exclusive bound.
