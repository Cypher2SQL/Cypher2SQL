package com.iisaka.cypher2sql.query.sql;

import com.iisaka.cypher2sql.StorageModel;

public interface SQLQuery<D extends StorageModel> {
    String render(D dialect);
}
