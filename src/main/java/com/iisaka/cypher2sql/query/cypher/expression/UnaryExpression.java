package com.iisaka.cypher2sql.query.cypher.expression;

public record UnaryExpression(Operator operator, Expression operand) implements Expression {
    @Override
    public boolean isAggregate() {
        return operand.isAggregate();
    }
}
