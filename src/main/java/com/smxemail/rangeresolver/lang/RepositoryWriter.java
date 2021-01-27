package com.smxemail.rangeresolver.lang;

import com.smxemail.rangeresolver.api.Alias;
import com.smxemail.rangeresolver.api.Allow;
import com.smxemail.rangeresolver.api.Bound;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.ResolveType;
import com.smxemail.rangeresolver.api.Resolved;
import io.vavr.collection.List;

import java.io.PrintWriter;
import java.io.Writer;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.ALLOW_SNAPSHOTS;
import static com.smxemail.rangeresolver.lang.RangeResolverVisitor.FILTER_SNAPSHOTS;

public class RepositoryWriter {

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
          pw.printf(
              "import %s:%s:%s;\n", imported.group(), imported.artifact(), imported.version());
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
      pw.println();

      for (Resolved resolved : resolveds) {
        printComments(pw, resolved.comments());
        pw.printf(
            command + " %s:%s:%s from %s;\n",
            resolved.group(),
            resolved.artifact(),
            resolved.version(),
            resolved.resolvedFrom());
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

  private static void printComments(PrintWriter pw, String comments) {
    if (!isNullOrEmpty(comments)) {
      pw.println(comments);
    }
  }
}
