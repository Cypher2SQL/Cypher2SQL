package com.iisaka.cypher2sql.query.sql;

public interface SqlDialect {
    String name();

    String quoteIdentifier(String identifier);
}
