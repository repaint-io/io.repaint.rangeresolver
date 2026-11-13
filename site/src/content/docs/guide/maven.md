---
title: Using with Apache Maven
description: Wiring pom.deps into a Maven build with the rangeresolver-maven-plugin.
---

Most projects use Range Resolver through **rangeresolver-maven-plugin**. The plugin reads `pom.deps`, resolves it,
writes the chosen versions back into your POMs, and publishes the script so other projects can import it.

## Add the plugin

Add the plugin to the top-level `pom.xml`. Two goals belong in the normal build:

- `resolve-properties` makes the locked versions available as properties.
- `attach-deps` publishes `pom.deps` alongside the project's other artifacts.

```xml title="pom.xml"
<build>
  <plugins>
    <plugin>
      <groupId>io.repaint</groupId>
      <artifactId>rangeresolver-maven-plugin</artifactId>
      <version><!-- latest release --></version>
      <executions>
        <execution>
          <id>resolve-properties</id>
          <phase>initialize</phase>
          <goals>
            <goal>resolve-properties</goal>
          </goals>
        </execution>
        <execution>
          <id>attach-deps</id>
          <phase>verify</phase>
          <goals>
            <goal>attach-deps</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

The other goals change files, so you run them by hand when you want to update dependencies.

## Where `pom.deps` lives

The plugin looks for the script next to the **top-level POM of the build**, with that POM's extension replaced by
`deps`. For a normal multi-module build, that is `pom.deps` beside the root `pom.xml`. One script covers the whole
reactor.

If the file is missing, every goal logs a warning and does nothing.

## Running the goals

`io.repaint` isn't one of Maven's default plugin groups, so either use the full coordinates:

```sh
mvn io.repaint:rangeresolver-maven-plugin:resolve-deps
```

or add the group to `~/.m2/settings.xml` once and use the short `rangeresolver:` prefix:

```xml title="~/.m2/settings.xml"
<pluginGroups>
  <pluginGroup>io.repaint</pluginGroup>
</pluginGroups>
```

```sh
mvn rangeresolver:resolve-deps
```

The examples below use the short form.

## The update cycle

```sh
mvn rangeresolver:resolve-deps   # resolve pom.deps and rewrite the POMs
git diff                         # review what moved
mvn verify                       # build and test with the new versions
mvn rangeresolver:lock-deps      # accept them: resolved → locked
```

Add `-U` to `resolve-deps` to check remote repositories for new versions now, rather than once a day.

To pick up newer releases of artifacts that come from an [import](../../language/imports/), name them for one run
without editing the script:

```sh
mvn rangeresolver:resolve-deps -DallowUnlocked='^com\.example:.*$'
```

## Goals

| Goal | Default phase | What it does |
| --- | --- | --- |
| `resolve-deps` | `initialize` | Resolves `pom.deps`, writes the results back to it, and rewrites the project files ([details](#what-resolve-deps-rewrites)). |
| `lock-deps` | `initialize` | Moves every `resolved` record to `locked`. |
| `unlock-deps` | `initialize` | Moves every `locked` record back to `resolved`. The next `resolve-deps` then no longer treats those versions as a minimum. |
| `update-lower-bounds` | `initialize` | Raises the lower bound of each `resolve` range to the newest version currently available in it. |
| `resolve-properties` | `initialize` | Sets a property for every `resolved`, `locked` and `deprecated` record in every module of the build ([details](#properties)). |
| `attach-deps` | `verify` | Attaches `pom.deps` to the build as classifier `bill-of-materials`, type `deps`, so it is installed and deployed with the project. |
| `verify` | `initialize` | Fails if the project declares a dependency that `pom.deps` doesn't cover ([details](#verify)). |

All goals except `verify` and `resolve-properties` run only once, at the root of the build.

## What `resolve-deps` rewrites

After resolving, `resolve-deps` updates the version of every artifact with a `resolved`, `locked`, `deprecated` or
`blacklisted` record wherever it appears in these files:

- **`pom.xml` and `tile.xml`** files, up to 10 directories below the root: dependencies, dependency management,
  plugins, plugin management, plugin dependencies, `maven-compiler-plugin` annotation processor paths, the parent
  version, properties named after a record's [property](#properties), and the same sections inside profiles.
- **Tiles** in a `tiles-maven-plugin` configuration, written as `group:artifact:[version]`.
- **`.mvn/extensions.xml`** build extensions.
- **`Dockerfile*`** `FROM` lines, which are pinned to the image digest from [`resolve docker`](../../language/docker/).

Artifacts built by the current reactor are never rewritten. Before changing a file, the plugin saves the original as
`<name>.orig`.

### Dependency versions

Dependency versions are written as exact ranges, such as `[2.0.17]`, so Maven can't substitute a different version
during conflict resolution. To write plain versions instead:

- `relaxedScopes`: a regex of dependency scopes, for example `test|provided`.
- `relaxedGavs`: a regex of `group:artifact` keys.

### Exclusions

When a `compile`, `runtime` or `provided` dependency has no `<exclusions>`, the plugin adds a wildcard exclusion:

```xml
<exclusions>
  <exclusion>
    <groupId>*</groupId>
    <artifactId>*</artifactId>
  </exclusion>
</exclusions>
```

This makes every transitive dependency explicit: anything the project needs must be declared, and so resolved. The
plugin leaves a dependency that already has `<exclusions>` unchanged.

For test-scoped dependencies, `testExclusions` lists `group:artifact` patterns (`*` wildcards allowed). Each
dependency matching one of them has every listed pattern added as an exclusion.

### Pruning unused records

With `filterUnusedDependencies` set, `resolve-deps` drops records for artifacts the build doesn't reference
anywhere. Keep specific artifacts with `retainUnusedDependencies`. That is useful when the script you
[import](../../language/imports/) locks far more than this project uses.

## Properties

`resolve-properties` sets one property per record, in every module of the build:

- each `resolved`, `locked` and `deprecated` record, under `<artifactId>.version` or its
  [alias](../../language/aliases/);
- each module in the reactor, under `<artifactId>.version`, set to the module's own version.

The properties are set in the `initialize` phase, so later plugins and resource filtering can use them:

```properties title="src/main/resources/versions.properties"
guava=${guava.version}
```

By then, Maven has already built the project model, so these properties don't reach `<parent>`, `<dependencies>`,
or `<plugins>` versions. Write those versions literally, or declare a POM property with the same name. Either way,
`resolve-deps` keeps them up to date:

```xml
<properties>
  <guava.version>33.8.0-jre</guava.version>   <!-- updated by resolve-deps -->
</properties>

<dependency>
  <groupId>com.google.guava</groupId>
  <artifactId>guava</artifactId>
  <version>${guava.version}</version>
</dependency>
```

## Verify

`verify` compares each module's dependencies and dependency management with the `resolved` and `locked` records.
For every undeclared dependency it logs an error and prints a line you can paste into `pom.deps`:

```text
[WARNING] Add the following script to the file /work/project/pom.deps:

     resolve highest org.slf4j:slf4j-api:[2.0.17,3.0.0);
```

The suggested range runs from the declared version up to the next major version.

Set `strictDependency` to `false` to log these errors without failing the build.

## Deprecated and blacklisted artifacts

| Parameter | Default | When `false` |
| --- | --- | --- |
| `allowDeprecatedDependencies` | `true` | `resolve-deps` and `resolve-properties` fail if a `deprecated` record applies to the build. |
| `allowBlacklistedDependencies` | `true` | `resolve-deps` fails if a `blacklisted` record applies to the build. |

Both are user properties, so you can set them in `<properties>` or on the command line:
`-DallowDeprecatedDependencies=false`.

## Releases

With `commitChanges` set, the goals that edit files commit their changes through the project's `<scm>` connection,
and `pushChanges` also pushes them. Commits happen **only during a release**, that is, when a `release.properties`
file is present, as it is while `maven-release-plugin` runs. This lets a release lock the versions it was built with.

## Parameter reference

| Parameter | User property | Default | Goals |
| --- | --- | --- | --- |
| `allowUnlocked` | `allowUnlocked` | none | all |
| `allowSnapshots` | `allowSnapshots` | none | all |
| `allowDeprecatedDependencies` | `allowDeprecatedDependencies` | `true` | `resolve-deps`, `resolve-properties` |
| `allowBlacklistedDependencies` | `allowBlacklistedDependencies` | `true` | `resolve-deps` |
| `filterUnusedDependencies` | `filterUnusedDependencies` | `false` | `resolve-deps` |
| `retainUnusedDependencies` | none | none | `resolve-deps` |
| `relaxedScopes` | `relaxedScopes` | none | `resolve-deps` |
| `relaxedGavs` | `relaxedGavs` | none | `resolve-deps` |
| `testExclusions` | `testExclusions` | none | `resolve-deps` |
| `prettyPrintPomFormat` | `prettyPrintPomFormat` | `false` | `resolve-deps` |
| `strictDependency` | `strictDependency` | `true` | `verify` |
| `commitChanges` | none | `false` | goals that edit files |
| `pushChanges` | none | `false` | goals that edit files |

`allowUnlocked` and `allowSnapshots` act like extra [`allow`](../../language/imports/#allow-unlocked) statements for
one run.
