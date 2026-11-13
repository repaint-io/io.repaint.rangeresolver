---
title: Getting started
description: Write your first pom.deps script and resolve it.
---

This walk-through builds a small `pom.deps` file from scratch and shows how it changes as it is resolved and locked.

## 1. Declare a repository

Every script that resolves Maven artifacts needs at least one repository.

```rangeresolver
repository https://repo1.maven.org/maven2/ as central;
```

The `as central` part names the repository. Names let you send different requests to different repositories, and
they double as repository ids, so `<server>` and `<mirror>` entries in `settings.xml` with `<id>central</id>` apply
to it.

## 2. Ask for some versions

```rangeresolver
repository https://repo1.maven.org/maven2/ as central;

resolve highest com.google.guava:guava:[33.0.0-jre,34.0.0) via central;
resolve lowest  org.slf4j:slf4j-api:[2.0.0,3.0.0) via central;
```

Each `resolve` statement has four parts:

| Part | Example | Meaning |
| --- | --- | --- |
| bound | `highest` | Which version in the range to pick: `lowest`, `highest` or `snapshot`. |
| coordinates | `com.google.guava:guava` | The Maven `groupId:artifactId`. |
| range | `[33.0.0-jre,34.0.0)` | A Maven version range. |
| `via` grouping | `via central` | Optional. Which named repositories to search. |

`lowest` is useful for libraries, where you want to compile against the oldest version you claim to support.
`highest` suits applications, where you want the newest compatible release.

## 3. Resolve

Running the resolver (with Maven, `mvn rangeresolver:resolve-deps`; see
[Using with Apache Maven](../maven/)) queries the repositories and appends what it found:

```rangeresolver
repository https://repo1.maven.org/maven2/ as central;

resolve highest com.google.guava:guava:[33.0.0-jre,34.0.0) via central;
resolve lowest org.slf4j:slf4j-api:[2.0.0,3.0.0) via central;

resolved com.google.guava:guava:33.8.0-jre;
resolved org.slf4j:slf4j-api:2.0.0;
```

The versions are published as the properties `guava.version` and `slf4j-api.version`, named after the artifact id.

## 4. Lock

Locking promotes every `resolved` record to `locked`:

```rangeresolver
locked com.google.guava:guava:33.8.0-jre;
locked org.slf4j:slf4j-api:2.0.0;
```

Commit the file. Builds now use exactly these versions.

## 5. Upgrade later

Run the resolver again some time later. If Guava `33.9.0-jre` has been released, the next run produces:

```rangeresolver
resolved com.google.guava:guava:33.9.0-jre;

locked org.slf4j:slf4j-api:2.0.0;
```

Nothing changes for SLF4J, because a newly resolved version replaces a locked one only when it is strictly newer.

## Next steps

- Learn every statement in the [language overview](../../language/overview/).
- Share versions between projects with [imports](../../language/imports/).
- Read [how resolution works](../resolution/) for the exact rules.
