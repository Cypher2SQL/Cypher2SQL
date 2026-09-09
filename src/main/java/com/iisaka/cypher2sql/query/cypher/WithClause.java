package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.List;

public record WithClause(
        List<ProjectionItem> items,
        boolean distinct,
        List<OrderItem> orderItems,
        Long skip,
        Long limit,
        Expression whereExpression,
        Cypher25Parser.WithClauseContext parseTreeNode) implements Clause {

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
