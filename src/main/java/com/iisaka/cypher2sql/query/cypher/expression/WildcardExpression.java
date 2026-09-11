package com.iisaka.cypher2sql.query.cypher.expression;

/** The {@code *} wildcard, as it appears in {@code count(*)}. A singleton since it carries no data. */
public enum WildcardExpression implements Expression {
    INSTANCE;

    @Override
    public boolean isAggregate() {
        return false;
    }
}
