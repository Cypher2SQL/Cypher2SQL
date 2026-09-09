package com.iisaka.cypher2sql.query.cypher;

import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.List;
import java.util.Set;

public record WithClause(
        List<ProjectionItem> items,
        boolean distinct,
        List<OrderItem> orderItems,
        Long skip,
        Long limit,
        Expression whereExpression,
        Cypher25Parser.WithClauseContext parseTreeNode) implements Clause {

    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of("count", "sum", "avg", "min", "max");

    public boolean hasMixedAggregation() {
        boolean anyAggregate = false;
        boolean anyNonAggregate = false;
        for (final ProjectionItem item : items) {
            if (isAggregate(item.expression())) {
                anyAggregate = true;
            } else {
                anyNonAggregate = true;
            }
        }
        return anyAggregate && anyNonAggregate;
    }

    private boolean isAggregate(final Expression expression) {
        return switch (expression) {
            case Expression.FunctionExpression function -> AGGREGATE_FUNCTIONS.contains(function.name().toLowerCase())
                    || function.arguments().stream().anyMatch(this::isAggregate);
            case Expression.PropertyExpression property -> isAggregate(property.receiver());
            case Expression.BinaryExpression binary -> isAggregate(binary.left()) || isAggregate(binary.right());
            case Expression.UnaryExpression unary -> isAggregate(unary.operand());
            case Expression.CaseExpression caseExpression -> (caseExpression.subject() != null && isAggregate(caseExpression.subject()))
                    || caseExpression.whenThens().stream()
                            .anyMatch(whenThen -> isAggregate(whenThen.whenExpression()) || isAggregate(whenThen.thenExpression()))
                    || (caseExpression.elseExpression() != null && isAggregate(caseExpression.elseExpression()));
            default -> false;
        };
    }
}
