package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;

public record ProjectionItem(Expression expression, String alias) {
    static ProjectionItem fromExpression(final Expression expression, final String alias) {
        return new ProjectionItem(expression, alias);
    }
}
