---
title: Repositories
description: Declaring the Maven repositories a script resolves against.
---

```text
repository <url> [as <name>] [cooldown <days>];
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

## Release cooldown

To avoid picking up a release the day it ships, add `cooldown <days>` to a repository. Versions found in that
repository that were published within that many days are held back, and each range resolves to the newest version
that is old enough:

```rangeresolver
repository https://repo1.maven.org/maven2/ cooldown 7;
repository https://repo.example.com/releases/ as internal;
```

The cooldown belongs to the repository, so you can hold back releases from a public repository while picking up
your own internal releases straight away. A repository without a `cooldown` clause has none. Where a mirror in
`settings.xml` stands in for several repositories, it uses the longest of their cooldowns.

Maven metadata doesn't record when each version was published, so the resolver sends a `HEAD` request for each
candidate version's POM and reads its `Last-Modified` header. It starts at the newest version and works down until
one is old enough, so usually only a few requests are needed per artifact. The request goes to the repository the
version was found in, with the credentials from your `settings.xml`. Versions in a repository without a cooldown are
never checked.

A version that exists only in your local repository, such as one you installed yourself, is looked up in each of the
request's repositories in turn, and the cooldown of the first one that has it applies.

A version is held back whenever its publish date can't be found, for example when the repository doesn't send
`Last-Modified`, or a locally installed version can't be found in any repository. Each held-back version is logged
with the reason.

Some things to be aware of:

- A repository manager that proxies Maven Central may report when it cached a POM rather than when it was
  published, so versions can stay held back for longer than the cooldown.
- The cooldown checks only the newest versions. A lower version published more recently, such as a backport
  release, isn't held back once a newer version has passed the cooldown.
- The `settings.xml` proxy configuration isn't used for these requests yet.
- Versions you've already locked stay locked. The cooldown only affects which new version a range moves to.

### Overriding the cooldown

The Maven plugin's `cooldownDays` parameter, or `withCooldownDays` in the Java API, replaces the cooldown of every
repository for that run. Set it to `0` to pick up an urgent fix the day it is released without editing the script:

```sh
mvn rangeresolver:resolve-deps -DcooldownDays=0
```

A positive value applies to every repository too, including ones declared without a cooldown. The override also
replaces any [`cooldown` declared on a request](../resolution/#cooldown).

### Cooling down a single artifact

A request can declare its own `cooldown`, which replaces the repository cooldown for that artifact. See
[Resolution requests](../resolution/#cooldown).

## Snapshot updates

Repositories are checked for new metadata once a day, which is Maven's default policy. Pass
`updateSnapshots = true` to the resolver to check on every run.
