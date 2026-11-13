---
title: Repositories
description: Declaring the Maven repositories a script resolves against.
---

```text
repository <url> [as <name>];
```

A `repository` statement adds a Maven repository to search.

```rangeresolver
repository https://repo1.maven.org/maven2/;
repository https://repo.example.com/releases/ as internal;
repository https://repo.example.com/snapshots/ as snapshots;
```

## URLs

Any `scheme://` URL is accepted, including ports, embedded credentials, query strings and `file://` URLs:

```rangeresolver
repository https://user:secret@nexus.example.com:8081/repository/releases?foo=bar;
repository file:///var/tmp/local-repository;
```

Prefer `settings.xml` for credentials over embedding them in the URL. See [Named repositories](#named-repositories).

## Groupings

The optional `as <name>` clause puts the repository in a named **grouping**. A repository without one goes into the
`default` grouping.

A [resolution request](../resolution/) searches one grouping: the one named by its `via` clause, or `default` if it
has none. Several repositories can share a name, and a request `via` that name searches all of them.

```rangeresolver
repository https://repo1.maven.org/maven2/ as central;
repository https://repo.example.com/releases/ as internal;

resolve highest org.slf4j:slf4j-api:[2.0.0,3.0.0) via central;
resolve highest com.example:widgets:[1.0.0,2.0.0) via internal;
```

[Imports](../imports/) are fetched from all declared repositories, whatever their grouping.

## Named repositories

A named repository uses its name as its Maven repository **id**. An unnamed repository uses its URL as its id. When
resolution runs inside Maven, the mirrors, proxies and `<server>` credentials in `settings.xml` are matched against
that id:

```xml title="~/.m2/settings.xml"
<servers>
  <server>
    <id>internal</id>
    <username>deployer</username>
    <password>${env.REPO_TOKEN}</password>
  </server>
</servers>
```

## Snapshot updates

Repositories are checked for new metadata once a day, which is Maven's default policy. Pass
`updateSnapshots = true` to the resolver to check on every run.
