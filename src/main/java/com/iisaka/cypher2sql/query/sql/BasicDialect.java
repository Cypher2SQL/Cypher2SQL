package com.iisaka.cypher2sql.query.sql;

import com.iisaka.cypher2sql.query.sql.Dialect;

public final class BasicDialect implements Dialect {
    @Override
    public String name() {
        return "basic";
    }

    @Override
    public String quoteIdentifier(final String identifier) {
        return "\"" + identifier + "\"";
    }
}
