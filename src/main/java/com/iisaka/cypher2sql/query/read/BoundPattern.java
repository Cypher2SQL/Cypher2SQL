package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BoundPattern {
    private final List<BoundNode> nodes;
    private final List<BoundTraversal> traversals;
    private final boolean optional;
    private final Expression localWhereExpression;

    public BoundPattern(
            final List<BoundNode> nodes,
            final List<BoundTraversal> traversals,
            final boolean optional,
            final Expression localWhereExpression) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.traversals = Collections.unmodifiableList(new ArrayList<>(traversals));
        if (this.nodes.size() != this.traversals.size() + 1) {
            throw new IllegalArgumentException("BoundPattern requires exactly one more node than traversal.");
        }
        this.optional = optional;
        this.localWhereExpression = localWhereExpression;
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

    public Expression localWhereExpression() {
        return localWhereExpression;
    }

    public BoundNode root() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("BoundPattern has no nodes.");
        }
        return nodes.get(0);
    }
}
