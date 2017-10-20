package com.smxemail.rangeresolver.lang;

import com.smxemail.rangeresolver.RangeResolverLexer;
import com.smxemail.rangeresolver.RangeResolverParser;
import com.smxemail.rangeresolver.api.RangeResolver;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;

import java.io.IOException;
import java.io.Reader;

public class RangeResolverProcessor {

  public static RangeResolver parseRangeResolver(
      String sourceReference, Reader reader, String allowUnlocked, String allowSnapshots)
      throws IOException {
    CharStream charStream = CharStreams.fromReader(reader);
    RangeResolverLexer lexer = new RangeResolverLexer(charStream);
    TokenStream tokens = new CommonTokenStream(lexer);
    RangeResolverParser parser = new RangeResolverParser(tokens);

    RangeResolverVisitor rangeResolverVisitor =
        new RangeResolverVisitor(sourceReference, allowUnlocked, allowSnapshots);
    RangeResolver rangeResolver = rangeResolverVisitor.visit(parser.resolver());
    return rangeResolver;
  }
}
