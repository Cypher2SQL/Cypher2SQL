package com.iisaka.cypher2sql.query.sql;

public interface Dialect {
    String name();

    String quoteIdentifier(String identifier);
}
