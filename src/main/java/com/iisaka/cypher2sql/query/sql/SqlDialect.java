package com.iisaka.cypher2sql.query.sql;

import com.iisaka.cypher2sql.StorageModel;

public interface SqlDialect extends StorageModel {
    String quoteIdentifier(String identifier);
}
