package com.iisaka.cypher2sql.query.sql;

public final class SqlJoin {
    public enum JoinType {
        INNER,
        LEFT
    }

    private final JoinType joinType;
    private final String table;
    private final String alias;
    private final String onCondition;

    public SqlJoin(final JoinType joinType, final String table, final String alias, final String onCondition) {
        this.joinType = joinType;
        this.table = table;
        this.alias = alias;
        this.onCondition = onCondition;
    }

    public JoinType joinType() {
        return joinType;
    }

    public String table() {
        return table;
    }

    public String alias() {
        return alias;
    }

    public String onCondition() {
        return onCondition;
    }
}
