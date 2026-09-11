package com.iisaka.cypher2sql.query.cypher.expression;

/** An operator usable in a {@link BinaryExpression} or {@link UnaryExpression}, paired with its SQL rendering. */
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

    /** This operator's SQL rendering. */
    public String sql() {
        return sql;
    }
}
