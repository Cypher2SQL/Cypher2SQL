package com.iisaka.cypher2sql.query.sql;

public interface Query<D extends Dialect> {
    String render(D dialect);
}
