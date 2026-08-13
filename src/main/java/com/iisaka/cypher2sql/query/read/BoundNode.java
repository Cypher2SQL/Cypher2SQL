package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.schema.NodeMapping;

public final class BoundNode {
    private final Node node;
    private final NodeMapping mapping;
    private final String alias;

    public BoundNode(final Node node, final NodeMapping mapping, final String alias) {
        this.node = node;
        this.mapping = mapping;
        this.alias = alias;
    }

    public Node node() {
        return node;
    }

    public NodeMapping mapping() {
        return mapping;
    }

    public String alias() {
        return alias;
    }

    public String variable() {
        return node.variable();
    }

    public String label() {
        return node.label();
    }
}
