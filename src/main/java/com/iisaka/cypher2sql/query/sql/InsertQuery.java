package com.iisaka.cypher2sql.query.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An {@code INSERT} statement builder. {@link #render} always throws; see {@link WriteQuery}. */
public final class InsertQuery implements WriteQuery {
    private final String table;
    private final Map<String, String> values = new LinkedHashMap<>();

    private InsertQuery(final String table) {
        this.table = table;
    }

    /** Starts an {@code INSERT INTO table}. */
    public static InsertQuery into(final String table) {
        return new InsertQuery(Objects.requireNonNull(table, "table"));
    }

    /** Sets a column's value expression. */
    public InsertQuery value(final String column, final String expression) {
        values.put(
                Objects.requireNonNull(column, "column"),
                Objects.requireNonNull(expression, "expression"));
        return this;
    }

    /** Whether no column values have been set yet. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public String render(final Grammar grammar) {
        // Placeholder only: write queries are intentionally disabled while the project is read-only.
        throw new UnsupportedOperationException(writeDisabledMessage());
    }
}
