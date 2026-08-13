package com.iisaka.cypher2sql.query.sql;

public interface Query<D extends Grammar> {
    String render(D grammar);
}
