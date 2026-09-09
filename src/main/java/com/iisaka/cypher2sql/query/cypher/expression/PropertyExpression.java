package com.iisaka.cypher2sql.query.cypher.expression;

public record PropertyExpression(Expression receiver, String property) implements Expression {
    @Override
    public boolean isAggregate() {
        return receiver.isAggregate();
    }
}
