package com.iisaka.cypher2sql.query.cypher.expression;

public enum Operator {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%"),
    EQUALS("="),
    NOT_EQUALS("<>"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    AND("AND"),
    OR("OR"),
    NOT("NOT"),
    NEGATE("-"),
    POSITIVE("+");

    private final String sql;

    Operator(final String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
