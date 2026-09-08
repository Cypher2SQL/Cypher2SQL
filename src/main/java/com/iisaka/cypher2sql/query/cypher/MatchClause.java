package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.read.BoundNode;
import com.iisaka.cypher2sql.query.read.BoundPattern;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record MatchClause(
        List<Pattern> patterns,
        boolean optional,
        Expression whereExpression,
        Cypher25Parser.MatchClauseContext parseTreeNode) implements Clause {

    public List<BoundPattern> bind(
            final SchemaDefinition schema,
            final Map<String, BoundNode> boundByVariable,
            final int[] nextAliasIndex) {
        final List<BoundPattern> boundPatterns = new ArrayList<>();
        for (final Pattern pattern : patterns) {
            boundPatterns.add(pattern.bind(schema, boundByVariable, nextAliasIndex, optional, whereExpression));
        }
        return boundPatterns;
    }
}
