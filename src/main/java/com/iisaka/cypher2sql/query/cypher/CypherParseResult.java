package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public record CypherParseResult(ParseTree parseTree, Parser parser, TokenStream tokens) {
}
