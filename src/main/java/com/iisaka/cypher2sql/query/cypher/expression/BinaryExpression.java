package com.iisaka.cypher2sql.query.cypher.expression;

public record BinaryExpression(Expression left, Operator operator, Expression right) implements Expression {
    @Override
    public boolean isAggregate() {
        return left.isAggregate() || right.isAggregate();
    }
}
