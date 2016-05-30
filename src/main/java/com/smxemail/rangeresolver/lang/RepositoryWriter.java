package com.smxemail.rangeresolver.lang;

import com.google.common.base.Strings;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;

import java.io.PrintWriter;
import java.io.Writer;

public class RepositoryWriter {

  public static void writeRangeResolveSource(RangeResolver rangeResolver, Writer writer) {

    try (PrintWriter pw = new PrintWriter(writer)) {

      if (!rangeResolver.repositories().isEmpty()) {
        for (Repository repository : rangeResolver.repositories()) {
          String comments = repository.comments();
          printComments(pw, comments);
          pw.printf("repository %s;\n", repository.url());
        }
      }

      if (!rangeResolver.imports().isEmpty()) {
        pw.println();

        for (Import imported : rangeResolver.imports()) {
          printComments(pw, imported.comments());
          pw.printf("import %s:%s:%s;\n", imported.group(), imported.artifact(), imported.version());
        }
      }

      if (!rangeResolver.resolves().isEmpty()) {
        pw.println();

        for (Resolve resolve : rangeResolver.resolves()) {
          printComments(pw, resolve.comments());
          pw.printf("resolve %s %s:%s:%s", resolve.bounds().toString().toLowerCase(), resolve.group(), resolve.artifact(), resolve.range().asVersionRange());
          if (!"".equals(resolve.alias())) {
            pw.printf(" as %s;\n", resolve.alias());
          } else {
            pw.printf(";\n");
          }
        }
      }

      if (!rangeResolver.resolved().isEmpty()) {
        pw.println();

        for (Resolved resolved : rangeResolver.resolved()) {
          printComments(pw, resolved.comments());
          pw.printf("resolved %s:%s:%s from %s;\n", resolved.group(), resolved.artifact(), resolved.version(), resolved.resolvedFrom());
        }
      }

      if (!rangeResolver.locked().isEmpty()) {
        pw.println();

        for (Resolved resolved : rangeResolver.locked()) {
          printComments(pw, resolved.comments());
          pw.printf("locked %s:%s:%s from %s;\n", resolved.group(), resolved.artifact(), resolved.version(), resolved.resolvedFrom());
        }
      }
    }
  }

  private static void printComments(PrintWriter pw, String comments) {
    if (!Strings.isNullOrEmpty(comments)) {
      pw.println(comments);
    }
  }
}
