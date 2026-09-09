package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;

public record OrderItem(Expression expression, boolean descending) {
}
