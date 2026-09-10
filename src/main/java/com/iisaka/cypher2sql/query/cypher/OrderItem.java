package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;

/**
 * One {@code ORDER BY} sort key.
 *
 * @param expression the expression to sort by
 * @param descending {@code true} for {@code DESC}, {@code false} for the default ascending order
 */
public record OrderItem(Expression expression, boolean descending) {
}
