package com.iisaka.cypher2sql.query.cypher.expression;

public enum WildcardExpression implements Expression {
    INSTANCE;

    @Override
    public boolean isAggregate() {
        return false;
    }
}
