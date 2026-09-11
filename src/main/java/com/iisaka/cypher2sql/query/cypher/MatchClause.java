package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;
import com.iisaka.cypher2sql.query.read.BoundNode;
import com.iisaka.cypher2sql.query.read.BoundPattern;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed {@code MATCH} or {@code OPTIONAL MATCH} clause.
 *
 * @param patterns       the graph patterns matched by this clause
 * @param optional       {@code true} for {@code OPTIONAL MATCH}, rendered as an outer join
 * @param whereExpression this clause's own {@code WHERE} predicate, or {@code null} if none
 * @param parseTreeNode  the originating ANTLR parse-tree node
 */
public record MatchClause(
        List<Pattern> patterns,
        boolean optional,
        Expression whereExpression,
        Cypher25Parser.MatchClauseContext parseTreeNode) implements Clause {

    /** Binds every pattern in this clause against the schema, extending {@code boundByVariable} in place. */
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
