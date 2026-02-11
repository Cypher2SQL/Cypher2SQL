package com.iisaka.cypher2sql.query.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SqlInsert implements SQLQuery<SqlDialect> {
    private final String table;
    private final Map<String, String> values = new LinkedHashMap<>();

    private SqlInsert(final String table) {
        this.table = table;
    }

    public static SqlInsert into(final String table) {
        return new SqlInsert(Objects.requireNonNull(table, "table"));
    }

    public SqlInsert value(final String column, final String expression) {
        values.put(
                Objects.requireNonNull(column, "column"),
                Objects.requireNonNull(expression, "expression"));
        return this;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public String render(final SqlDialect dialect) {
        // Placeholder only: write queries are intentionally disabled while the project is read-only.
        throw new UnsupportedOperationException(
                "Write queries are disabled in read-only mode. SqlInsert is reserved for future enhancement.");
    }
}
