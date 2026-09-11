package com.iisaka.cypher2sql.query.sql;

/** One {@code JOIN} in a {@link SelectQuery}. */
public final class JoinClause {
    /** {@code INNER} for a {@code MATCH}, {@code LEFT} for an {@code OPTIONAL MATCH}. */
    public enum JoinType {
        INNER,
        LEFT
    }

    private final JoinType joinType;
    private final String table;
    private final String alias;
    private final String onCondition;

    public JoinClause(final JoinType joinType, final String table, final String alias, final String onCondition) {
        this.joinType = joinType;
        this.table = table;
        this.alias = alias;
        this.onCondition = onCondition;
    }

    /** Whether this is an {@code INNER} or {@code LEFT} join. */
    public JoinType joinType() {
        return joinType;
    }

    /** The joined table's unqualified name. */
    public String table() {
        return table;
    }

    /** The alias assigned to the joined table. */
    public String alias() {
        return alias;
    }

    /** The already-rendered {@code ON} condition SQL. */
    public String onCondition() {
        return onCondition;
    }
}
