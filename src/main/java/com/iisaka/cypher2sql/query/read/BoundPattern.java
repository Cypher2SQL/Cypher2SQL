package com.iisaka.cypher2sql.query.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BoundPattern {
    private final List<BoundNode> nodes;
    private final List<BoundTraversal> traversals;
    private final boolean optional;

    public BoundPattern(final List<BoundNode> nodes, final List<BoundTraversal> traversals, final boolean optional) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.traversals = Collections.unmodifiableList(new ArrayList<>(traversals));
        if (this.nodes.size() != this.traversals.size() + 1) {
            throw new IllegalArgumentException("BoundPattern requires exactly one more node than traversal.");
        }
        this.optional = optional;
    }

    public List<BoundNode> nodes() {
        return nodes;
    }

    public List<BoundTraversal> traversals() {
        return traversals;
    }

    public boolean optional() {
        return optional;
    }

    public BoundNode root() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("BoundPattern has no nodes.");
        }
        return nodes.get(0);
    }
}
