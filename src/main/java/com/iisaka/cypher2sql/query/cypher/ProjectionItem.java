package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;

/**
 * One item projected by a {@code RETURN} or {@code WITH} clause.
 *
 * @param expression the projected expression
 * @param alias      the {@code AS} alias, or {@code null} if unaliased
 */
public record ProjectionItem(Expression expression, String alias) {
    static ProjectionItem fromExpression(final Expression expression, final String alias) {
        return new ProjectionItem(expression, alias);
    }
}
