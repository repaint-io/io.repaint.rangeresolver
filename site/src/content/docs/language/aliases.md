---
title: Aliases
description: Publishing a resolved version under a property name of your choice.
---

```text
alias <group>:<artifact> as <property.name>;
```

By default, a resolved version is published as the property `<artifactId>.version`:

| Artifact | Default property |
| --- | --- |
| `com.google.guava:guava` | `guava.version` |
| `org.immutables:value` | `value.version` |
| `com.github.spullara.mustache.java:compiler` | `compiler.version` |

Short artifact ids like `value` and `compiler` make poor property names, and two artifacts from different groups can
share an id. An `alias` sets the name instead:

```rangeresolver
resolve highest org.immutables:value:[2.10.0,3.0.0);
resolve highest com.github.spullara.mustache.java:compiler:[0.9.0,1.0.0);

alias org.immutables:value as immutables.value.version;
alias com.github.spullara.mustache.java:compiler as mustache.compiler.version;
```

The POM can then refer to `${immutables.value.version}`.

An alias applies to the artifact's `resolve` requests and to its `resolved`, `locked`, `deprecated` and
`blacklisted` records in the same script. Declaring two aliases for the same artifact is an error.
