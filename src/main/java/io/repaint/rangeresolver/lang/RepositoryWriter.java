package io.repaint.rangeresolver.lang;

import dev.zazr.collection.List;

import io.repaint.rangeresolver.api.Alias;
import io.repaint.rangeresolver.api.Allow;
import io.repaint.rangeresolver.api.Bound;
import io.repaint.rangeresolver.api.Import;
import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Repository;
import io.repaint.rangeresolver.api.Resolve;
import io.repaint.rangeresolver.api.ResolveType;
import io.repaint.rangeresolver.api.Resolved;

import java.io.PrintWriter;
import java.io.Writer;

import static com.google.common.base.Strings.isNullOrEmpty;

import static io.repaint.rangeresolver.api.ArtifactType.DOCKER;
import static io.repaint.rangeresolver.lang.RangeResolverVisitor.ALLOW_SNAPSHOTS;
import static io.repaint.rangeresolver.lang.RangeResolverVisitor.FILTER_SNAPSHOTS;

public final class RepositoryWriter {
  public static void writeRangeResolveSource(RangeResolver rangeResolver, Writer writer) {
    try (PrintWriter pw = new PrintWriter(writer)) {
      if (!rangeResolver.repositories().isEmpty()) {
        for (Repository repository : rangeResolver.repositories().values()) {
          String comments = repository.comments();
          printComments(pw, comments);
          pw.printf("repository %s", repository.url());
          if (!repository.grouping().equals("default")) {
            pw.printf(" as %s", repository.grouping());
          }
          pw.printf(";\n");
        }
      }

      if (!rangeResolver.imports().isEmpty()) {
        pw.println();

        for (Import imported : rangeResolver.imports()) {
          printComments(pw, imported.comments());
          pw.printf("import %s:%s:%s;\n", imported.group(), imported.artifact(), imported.version());
        }
      }

      if (!rangeResolver.allows().isEmpty()) {
        pw.println();

        for (Allow allow : rangeResolver.allows()) {
          if (allow.persistent()) {
            printComments(pw, allow.comments());
            pw.printf("allow %s /%s/;\n", allow.allowable().name().toLowerCase(), allow.pattern());
          }
        }
      }

      if (!rangeResolver.withheld().isEmpty()) {
        pw.println();

        for (Allow allow : rangeResolver.withheld()) {
          if (allow.persistent()) {
            printComments(pw, allow.comments());
            pw.printf("withhold /%s/;\n", allow.pattern());
          }
        }
      }

      printResolution(pw, "blacklist", ResolveType.BLACKLIST.from(rangeResolver));
      printResolution(pw, "deprecate", ResolveType.DEPRECATE.from(rangeResolver));
      printDockerResolution(pw, "resolve docker", ResolveType.DOCKER.from(rangeResolver));
      printResolution(pw, "resolve", ResolveType.RESOLVE.from(rangeResolver));
      printResolution(pw, "override", ResolveType.OVERRIDE.from(rangeResolver));

      if (!rangeResolver.aliases().isEmpty()) {
        pw.println();

        for (Alias alias : rangeResolver.aliases()) {
          printComments(pw, alias.comments());
          pw.printf("alias %s:%s as %s;\n", alias.group(), alias.artifact(), alias.alias());
        }
      }

      printResolved(pw, "blacklisted", rangeResolver.blacklisted());
      printResolved(pw, "deprecated", rangeResolver.deprecated());
      printResolved(pw, "resolved", rangeResolver.resolved());
      printResolved(pw, "locked", rangeResolver.locked());
    }
  }

  private static void printResolved(PrintWriter pw, String command, List<Resolved> resolveds) {
    if (!resolveds.isEmpty()) {
      var groupedResolved = resolveds.groupBy(Resolved::artifactType);

      for (var group : groupedResolved) {
        pw.println();

        for (Resolved resolved : group._2()) {
          printComments(pw, resolved.comments());
          if (resolved.artifactType() == DOCKER) {
            pw.printf(command + " %s@%s;\n", resolved.artifact(), resolved.version());
          } else {
            pw.printf(command + " %s:%s:%s;\n", resolved.group(), resolved.artifact(), resolved.version());
          }
        }
      }
    }
  }

  private static void printResolution(PrintWriter pw, String command, List<Resolve> resolves) {
    if (!resolves.isEmpty()) {
      pw.println();

      for (Resolve resolve : resolves) {
        String boundSpec = resolve.bounds().toString().toLowerCase();
        String versionRange = resolve.range().asVersionRange();
        String gav = String.format("%s:%s:%s", resolve.group(), resolve.artifact(), versionRange);

        printComments(pw, resolve.comments());
        pw.printf("%s %s %s", command, boundSpec, gav);

        if (!resolve.grouping().equals("default")) {
          pw.printf(" via %s", resolve.grouping());
        }

        if (resolve.bounds() != Bound.SNAPSHOT
            && resolve.filtering() != ALLOW_SNAPSHOTS
            && resolve.filtering() != FILTER_SNAPSHOTS) {
          pw.printf(" filtering /%s/", resolve.filtering());
        }
        pw.printf(";\n");
      }
    }
  }

  private static void printDockerResolution(PrintWriter pw, String command, List<Resolve> resolveds) {
    if (!resolveds.isEmpty()) {
      pw.println();

      for (Resolve resolve : resolveds) {
        printComments(pw, resolve.comments());
        pw.printf(command + " %s;\n", resolve.artifact());
      }
    }
  }

  private static void printComments(PrintWriter pw, String comments) {
    if (!isNullOrEmpty(comments)) {
      pw.println(comments);
    }
  }

  private RepositoryWriter() {}
}
