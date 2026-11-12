package io.repaint.rangeresolver.lang;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Collects lexer and parser errors so that parsing can fail loudly once the whole file is read.
final class SyntaxErrorCollector extends BaseErrorListener {
  private final String sourceReference;

  private final List<String> errors = new ArrayList<>();

  SyntaxErrorCollector(String sourceReference) {
    this.sourceReference = sourceReference;
  }

  @Override
  public void syntaxError(
      @Nullable Recognizer<?, ?> recognizer,
      @Nullable Object offendingSymbol,
      int line,
      int charPositionInLine,
      @Nullable String msg,
      @Nullable RecognitionException e) {
    errors.add("line %d:%d %s".formatted(line, charPositionInLine, msg));
  }

  void failOnErrors() {
    if (errors.isEmpty()) {
      return;
    }

    String message = "%s in %s: %s"
        .formatted(
            errors.size() == 1 ? "Syntax error" : "Syntax errors",
            sourceReference,
            String.join("; ", errors));

    throw new RangeResolverSyntaxException(message, errors);
  }
}
