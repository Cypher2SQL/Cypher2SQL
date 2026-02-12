package com.iisaka.cypher2sql.query.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DeleteQuery implements Query<Dialect> {
    private final String table;
    private final List<String> whereClauses = new ArrayList<>();

    private DeleteQuery(final String table) {
        this.table = table;
    }

    public static DeleteQuery from(final String table) {
        return new DeleteQuery(Objects.requireNonNull(table, "table"));
    }

    public DeleteQuery where(final String clause) {
        whereClauses.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    public boolean hasWhereClause() {
        return !whereClauses.isEmpty();
    }

    @Override
    public String render(final Dialect dialect) {
        // Placeholder only: write queries are intentionally disabled while the project is read-only.
        throw new UnsupportedOperationException(
                "Write queries are disabled in read-only mode. DeleteQuery is reserved for future enhancement.");
    }
}
