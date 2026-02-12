package com.iisaka.cypher2sql.query.sql;

public interface SQLQuery<D extends SqlDialect> {
    String render(D dialect);
}
