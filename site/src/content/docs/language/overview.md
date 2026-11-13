---
title: Language overview
description: The shape of a Range Resolver script and the rules every statement follows.
---

A Range Resolver script is a flat list of **statements**. Each statement starts with a keyword and ends with a
semicolon. There are no blocks, no variables and no expressions.

```rangeresolver
// Comments run to the end of the line.
repository https://repo1.maven.org/maven2/ as central;

import com.example:platform-bom:4.2.0;
allow unlocked /^com\.example:.*$/;

resolve highest org.slf4j:slf4j-api:[2.0.0,3.0.0) via central;

alias org.slf4j:slf4j-api as slf4j.version;

locked org.slf4j:slf4j-api:2.0.17;
```

## Statement families

The statements fall into three groups.

### Configuration

| Statement | Purpose |
| --- | --- |
| [`repository`](../repositories/) | A Maven repository to query. |
| [`import`](../imports/) | Inherit versions from another project's published script. |
| [`allow`](../imports/#allow-unlocked) | Patterns of imported artifacts you may re-resolve, or resolve as snapshots. |
| [`withhold`](../imports/#withhold) | Patterns that are never re-resolved, even when an `allow` matches. |
| [`alias`](../aliases/) | Publish an artifact's version under a different property name. |

### Requests

Requests are what you write by hand. Each one says which versions you are willing to accept.

| Statement | Purpose |
| --- | --- |
| [`resolve`](../resolution/) | Resolve a version from a range. |
| [`override`](../resolution/#override) | Resolve a version even if an import or a lock says otherwise. |
| [`deprecate`](../resolution/#deprecate-and-blacklist) | Mark the matching version as deprecated. |
| [`blacklist`](../resolution/#deprecate-and-blacklist) | Mark the matching version as blacklisted. |
| [`resolve docker`](../docker/) | Pin a Docker image tag to its digest. |

### Records

Records are written by the resolver. They state what was chosen.

| Statement | Purpose |
| --- | --- |
| [`resolved`](../records/) | Picked by the most recent resolution and not yet locked. |
| [`locked`](../records/) | Pinned. Builds use this version. |
| [`deprecated`](../records/) | A version flagged as deprecated. |
| [`blacklisted`](../records/) | A version flagged as blacklisted. |

## Lexical rules

**Whitespace** (spaces, tabs and newlines) separates tokens and is otherwise ignored. A statement may span several
lines.

**Comments** start with `//` and run to the end of the line. A comment is attached to the statement that **follows**
it, and the writer keeps it with that statement when the file is rewritten. Comments after the last statement are
allowed and discarded.

```rangeresolver
// Kept with the repository below, even if the file is reordered.
repository https://repo1.maven.org/maven2/;
```

**Identifiers** cover group ids, artifact ids, versions and names. They may contain letters, digits, `.`, `-`, `_`,
`+`, `~`, `/` and `\`.

**Keywords are contextual.** Every keyword is also a valid identifier, so artifacts called `locked` or `docker`, or a
repository named `snapshots`, need no quoting:

```rangeresolver
repository https://repo.example.com/snapshots as snapshots;
resolve lowest com.example:locked:[1.0.0,2.0.0) via snapshots;
```

**Regular expressions** are written between slashes: `/^com\.example:.*$/`. Everything up to the next unescaped `/`
belongs to the pattern, and `\/` escapes a slash. Patterns use `java.util.regex` syntax, ignore case, and must
match the **whole** string.

**URLs** are any `scheme://…` text up to the next whitespace or `;`.

## Errors

The parser is strict. A script with any syntax error is rejected outright, with the location of the first problem:

```text
Syntax error in pom.deps: line 2:0 mismatched input 'resolve' expecting {'filtering', 'via', ';'}
```

The most common cause is a missing `;` at the end of the previous line.
