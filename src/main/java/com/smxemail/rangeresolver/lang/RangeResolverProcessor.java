package com.smxemail.rangeresolver.lang;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;

import com.smxemail.rangeresolver.RangeResolverLexer;
import com.smxemail.rangeresolver.RangeResolverParser;
import com.smxemail.rangeresolver.api.RangeResolver;

import java.io.IOException;
import java.io.Reader;

public final class RangeResolverProcessor {
  /// Parses a range resolver script into its model. Unlike earlier versions, a script that does not parse cleanly is
  /// rejected outright rather than being silently error-recovered into a partial model.
  ///
  /// @throws RangeResolverSyntaxException if the script contains any lexer or parser errors.
  public static RangeResolver parseRangeResolver(
      String sourceReference, Reader reader, String allowUnlocked, String allowSnapshots) throws IOException {
    CharStream charStream = CharStreams.fromReader(reader);
    SyntaxErrorCollector errorCollector = new SyntaxErrorCollector(sourceReference);

    RangeResolverLexer lexer = new RangeResolverLexer(charStream);
    lexer.removeErrorListeners();
    lexer.addErrorListener(errorCollector);

    TokenStream tokens = new CommonTokenStream(lexer);
    RangeResolverParser parser = new RangeResolverParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(errorCollector);

    RangeResolverParser.ResolverContext resolver = parser.resolver();
    errorCollector.failOnErrors();

    RangeResolverVisitor rangeResolverVisitor =
        new RangeResolverVisitor(sourceReference, allowUnlocked, allowSnapshots);
    return rangeResolverVisitor.visit(resolver);
  }

  private RangeResolverProcessor() {}
}
