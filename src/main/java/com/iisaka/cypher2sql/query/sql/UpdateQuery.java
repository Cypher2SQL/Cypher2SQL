package com.iisaka.cypher2sql.query.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An {@code UPDATE} statement builder. {@link #render} always throws; see {@link WriteQuery}. */
public final class UpdateQuery implements WriteQuery {
    private final String table;
    private final Map<String, String> assignments = new LinkedHashMap<>();
    private final java.util.List<String> whereClauses = new java.util.ArrayList<>();

    private UpdateQuery(final String table) {
        this.table = table;
    }

    /** Starts an {@code UPDATE table}. */
    public static UpdateQuery table(final String table) {
        return new UpdateQuery(Objects.requireNonNull(table, "table"));
    }

    /** Adds a {@code SET column = expression} assignment. */
    public UpdateQuery set(final String column, final String expression) {
        assignments.put(
                Objects.requireNonNull(column, "column"),
                Objects.requireNonNull(expression, "expression"));
        return this;
    }

    /** Adds a (conjoined) {@code WHERE} clause. */
    public UpdateQuery where(final String clause) {
        whereClauses.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    /** Whether any column assignment has been added. */
    public boolean hasAssignments() {
        return !assignments.isEmpty();
    }

    /** Whether any {@code WHERE} clause has been added. */
    public boolean hasWhereClause() {
        return !whereClauses.isEmpty();
    }

    @Override
    public String render(final Grammar grammar) {
        // Placeholder only: write queries are intentionally disabled while the project is read-only.
        throw new UnsupportedOperationException(writeDisabledMessage());
    }
}
