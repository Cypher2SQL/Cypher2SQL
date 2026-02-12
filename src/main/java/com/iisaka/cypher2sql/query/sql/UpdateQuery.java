package com.iisaka.cypher2sql.query.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class UpdateQuery implements Query<Dialect> {
    private final String table;
    private final Map<String, String> assignments = new LinkedHashMap<>();
    private final java.util.List<String> whereClauses = new java.util.ArrayList<>();

    private UpdateQuery(final String table) {
        this.table = table;
    }

    public static UpdateQuery table(final String table) {
        return new UpdateQuery(Objects.requireNonNull(table, "table"));
    }

    public UpdateQuery set(final String column, final String expression) {
        assignments.put(
                Objects.requireNonNull(column, "column"),
                Objects.requireNonNull(expression, "expression"));
        return this;
    }

    public UpdateQuery where(final String clause) {
        whereClauses.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    public boolean hasAssignments() {
        return !assignments.isEmpty();
    }

    public boolean hasWhereClause() {
        return !whereClauses.isEmpty();
    }

    @Override
    public String render(final Dialect dialect) {
        // Placeholder only: write queries are intentionally disabled while the project is read-only.
        throw new UnsupportedOperationException(
                "Write queries are disabled in read-only mode. UpdateQuery is reserved for future enhancement.");
    }
}
