package com.iisaka.cypher2sql.query.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A {@code DELETE} statement builder. {@link #render} always throws; see {@link WriteQuery}. */
public final class DeleteQuery implements WriteQuery {
    private final String table;
    private final List<String> whereClauses = new ArrayList<>();

    private DeleteQuery(final String table) {
        this.table = table;
    }

    /** Starts a {@code DELETE FROM table}. */
    public static DeleteQuery from(final String table) {
        return new DeleteQuery(Objects.requireNonNull(table, "table"));
    }

    /** Adds a (conjoined) {@code WHERE} clause. */
    public DeleteQuery where(final String clause) {
        whereClauses.add(Objects.requireNonNull(clause, "clause"));
        return this;
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
