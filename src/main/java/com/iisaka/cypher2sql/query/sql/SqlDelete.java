package com.iisaka.cypher2sql.query.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqlDelete implements SQLQuery<SqlDialect> {
    private final String table;
    private final List<String> whereClauses = new ArrayList<>();

    private SqlDelete(final String table) {
        this.table = table;
    }

    public static SqlDelete from(final String table) {
        return new SqlDelete(Objects.requireNonNull(table, "table"));
    }

    public SqlDelete where(final String clause) {
        whereClauses.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    public boolean hasWhereClause() {
        return !whereClauses.isEmpty();
    }

    @Override
    public String render(final SqlDialect dialect) {
        // Placeholder only: write queries are intentionally disabled while the project is read-only.
        throw new UnsupportedOperationException(
                "Write queries are disabled in read-only mode. SqlDelete is reserved for future enhancement.");
    }
}
