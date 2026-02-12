package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

// Explicit ANTLR parse artifacts used by downstream extractors.
public record CypherParseTree(ParseTree parseTree, Parser parser) {
}
