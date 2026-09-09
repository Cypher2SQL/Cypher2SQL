package com.iisaka.cypher2sql.query.sql;

public sealed interface WriteQuery extends Query<Grammar> permits InsertQuery, UpdateQuery, DeleteQuery {
    default String writeDisabledMessage() {
        return "Write queries are disabled in read-only mode. " + getClass().getSimpleName() + " is reserved for future enhancement.";
    }
}
