package com.iisaka.cypher2sql.query.cypher;

public final class CypherNode {
    private final String variable;
    private final String label;

    public CypherNode(final String variable, final String label) {
        this.variable = variable;
        this.label = label;
    }

    public String variable() {
        return variable;
    }

    public String label() {
        return label;
    }
}
