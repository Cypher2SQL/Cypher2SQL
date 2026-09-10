package com.iisaka.cypher2sql.query.cypher.expression;

/**
 * A two-operand expression, e.g. {@code p.age > 18} or {@code a AND b}.
 *
 * @param left     the left-hand operand
 * @param operator the binary operator
 * @param right    the right-hand operand
 */
public record BinaryExpression(Expression left, Operator operator, Expression right) implements Expression {
    @Override
    public boolean isAggregate() {
        return left.isAggregate() || right.isAggregate();
    }
}
