package com.iisaka.cypher2sql.schema;

public final class NodeMapping {
    private final String label;
    private final String table;
    private final String primaryKey;

    public NodeMapping(final String label, final String table, final String primaryKey) {
        this.label = label;
        this.table = table;
        this.primaryKey = primaryKey;
    }

    public String label() {
        return label;
    }

    public String table() {
        return table;
    }

    public String primaryKey() {
        return primaryKey;
    }
}
