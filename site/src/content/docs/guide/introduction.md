---
title: Introduction
description: What Range Resolver is, and the problem it solves.
---

**Range Resolver** is a Java library that defines a small, line-oriented language for managing Maven dependency
versions. A script, conventionally named `pom.deps` and kept next to `pom.xml`, says three things:

1. **Where to look**: the Maven repositories to query (`repository`).
2. **What you will accept**: a version range and a selection strategy for each artifact (`resolve`, `override`,
   `deprecate`, `blacklist`).
3. **What was chosen**: the concrete versions found by the last resolution (`resolved`, `locked`, `deprecated`,
   `blacklisted`).

The library parses the script into an immutable model, resolves the requests against real repositories using
[Maven Resolver](https://maven.apache.org/resolver/), and writes the model back out as a script. The file is both
the request and the lock file.

## Why not just use version ranges in the POM?

Maven can resolve ranges such as `[2.0,3.0)` directly, but then every build may pick a different version, and nothing
records which one was used. Pinning exact versions avoids that, but then upgrades are manual and easy to forget.

Range Resolver separates the two:

- You state the *intent* once: "any 2.x release of this artifact, newest first, no release candidates".
- A resolution run turns that intent into a concrete version and records it.
- Builds use the recorded version until you choose to resolve again.

Resolution only ever moves a version **forward**. If a locked version is already newer than anything the range would
pick, it stays where it is.

## Where it fits

This repository holds the language: the ANTLR grammar, the parser, the model, the resolver and the writer. Most users
reach it through the companion **rangeresolver-maven-plugin**. The plugin reads `pom.deps`, resolves it, writes
the chosen versions into your POMs, and publishes them as Maven properties (`jackson-databind.version` and so on).
See [Using with Apache Maven](../maven/).

You can also embed the library directly. See [Using the Java API](../java-api/).

## Coordinates

```xml
<dependency>
  <groupId>io.repaint</groupId>
  <artifactId>io.repaint.rangeresolver</artifactId>
  <version><!-- latest release --></version>
</dependency>
```

The library targets Java 26.
