package com.iisaka.cypher2sql.query.cypher.expression;

/**
 * A single-operand expression, e.g. {@code NOT p.active} or {@code -p.balance}.
 *
 * @param operator the unary operator
 * @param operand  the operand expression
 */
public record UnaryExpression(Operator operator, Expression operand) implements Expression {
    @Override
    public boolean isAggregate() {
        return operand.isAggregate();
    }
}
