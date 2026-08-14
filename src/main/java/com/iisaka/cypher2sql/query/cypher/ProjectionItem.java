package com.iisaka.cypher2sql.query.cypher;

public record ProjectionItem(Expression expression, String alias) {
    static ProjectionItem fromProjectionExpression(final String expression, final String alias) {
        return new ProjectionItem(Expression.parse(expression), alias);
    }

    static ProjectionItem fromExpression(final Expression expression, final String alias) {
        return new ProjectionItem(expression, alias);
    }
}
