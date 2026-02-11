package com.iisaka.cypher2sql.query.sql;

import com.iisaka.cypher2sql.query.sql.SqlDialect;

public final class BasicSqlDialect implements SqlDialect {
    @Override
    public String name() {
        return "basic";
    }

    @Override
    public String quoteIdentifier(final String identifier) {
        return "\"" + identifier + "\"";
    }
}
