package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.List;

/**
 * A parsed {@code WITH} clause, forming an intermediate projection stage between {@code MATCH} and
 * {@code RETURN}. Only a single {@code WITH} clause per query is currently supported.
 *
 * @param items          the projected expressions
 * @param distinct       whether {@code DISTINCT} was specified
 * @param orderItems     this clause's own {@code ORDER BY} keys
 * @param skip           the {@code SKIP} count, or {@code null} if absent
 * @param limit          the {@code LIMIT} count, or {@code null} if absent
 * @param whereExpression this clause's own {@code WHERE} predicate, or {@code null} if none
 * @param parseTreeNode  the originating ANTLR parse-tree node
 */
public record WithClause(
        List<ProjectionItem> items,
        boolean distinct,
        List<OrderItem> orderItems,
        Long skip,
        Long limit,
        Expression whereExpression,
        Cypher25Parser.WithClauseContext parseTreeNode) implements Clause {

    /** Whether {@link #items()} mixes aggregate and non-aggregate expressions, which is not supported. */
    public boolean hasMixedAggregation() {
        boolean anyAggregate = false;
        boolean anyNonAggregate = false;
        for (final ProjectionItem item : items) {
            if (item.expression().isAggregate()) {
                anyAggregate = true;
            } else {
                anyNonAggregate = true;
            }
        }
        return anyAggregate && anyNonAggregate;
    }
}
