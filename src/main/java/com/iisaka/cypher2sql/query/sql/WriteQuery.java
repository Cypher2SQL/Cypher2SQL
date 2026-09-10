package com.iisaka.cypher2sql.query.sql;

/**
 * A write-side SQL statement builder ({@code INSERT}/{@code UPDATE}/{@code DELETE}). These exist only as
 * placeholders for a future write-mode enhancement: {@link #render} always throws, since this project is
 * read-only.
 */
public sealed interface WriteQuery extends Query<Grammar> permits InsertQuery, UpdateQuery, DeleteQuery {
    /** The {@link UnsupportedOperationException} message every implementation's {@link #render} throws. */
    default String writeDisabledMessage() {
        return "Write queries are disabled in read-only mode. " + getClass().getSimpleName() + " is reserved for future enhancement.";
    }
}
