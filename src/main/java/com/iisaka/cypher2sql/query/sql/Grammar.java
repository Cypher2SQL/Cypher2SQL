package com.iisaka.cypher2sql.query.sql;

public interface Grammar {
    String name();

    String quoteIdentifier(String identifier);
}
