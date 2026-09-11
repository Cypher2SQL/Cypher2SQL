package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.schema.NodeMapping;

/** A Cypher {@link Node} resolved against a {@link NodeMapping} and assigned a SQL table alias. */
public final class BoundNode {
    private final Node node;
    private final NodeMapping mapping;
    private final String alias;

    public BoundNode(final Node node, final NodeMapping mapping, final String alias) {
        this.node = node;
        this.mapping = mapping;
        this.alias = alias;
    }

    /** The unresolved Cypher node this was bound from. */
    public Node node() {
        return node;
    }

    /** The schema mapping this node's label resolved to. */
    public NodeMapping mapping() {
        return mapping;
    }

    /** The SQL table alias assigned to this node, e.g. {@code t0}. */
    public String alias() {
        return alias;
    }

    /** The node's Cypher variable name, or {@code null} if anonymous. */
    public String variable() {
        return node.variable();
    }

    /** The node's resolved label. */
    public String label() {
        return node.label();
    }
}
