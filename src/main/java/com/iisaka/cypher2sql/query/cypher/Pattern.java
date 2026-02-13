package com.iisaka.cypher2sql.query.cypher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Pattern {
    private final List<Node> nodes;
    private final List<Edge> edges;

    public Pattern(final List<Node> nodes, final List<Edge> edges) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public Node rootNode() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Pattern has no nodes.");
        }
        return nodes.get(0);
    }

    public Node nodeAt(final int index) {
        return nodes.get(index);
    }

    public Edge edgeAt(final int index) {
        return edges.get(index);
    }

    public String aliasAt(final int nodeIndex) {
        return "t" + nodeIndex;
    }

    public String rootAlias() {
        return aliasAt(0);
    }

    public String aliasForVariable(final String variable) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            if (variable.equals(node.variable())) {
                return aliasAt(i);
            }
        }
        return null;
    }
}
