package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A {@link com.iisaka.cypher2sql.query.cypher.Pattern} whose nodes and edges have all been bound and aliased. */
public final class BoundPattern {
    private final List<BoundNode> nodes;
    private final List<BoundTraversal> traversals;
    private final boolean optional;
    private final Expression localWhereExpression;

    /** @throws IllegalArgumentException if {@code nodes} does not have exactly one more element than {@code traversals} */
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

    /** The pattern's bound nodes, in traversal order. */
    public List<BoundNode> nodes() {
        return nodes;
    }

    /** The pattern's bound relationship traversals connecting consecutive nodes. */
    public List<BoundTraversal> traversals() {
        return traversals;
    }

    /** Whether this pattern came from an {@code OPTIONAL MATCH}, rendered as an outer join. */
    public boolean optional() {
        return optional;
    }

    /** This pattern's own {@code WHERE} predicate (from its enclosing {@code MATCH} clause), or {@code null}. */
    public Expression localWhereExpression() {
        return localWhereExpression;
    }

    /**
     * The first node in the pattern, from which its FROM clause or outer join originates.
     *
     * @throws IllegalStateException if the pattern has no nodes
     */
    public BoundNode root() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("BoundPattern has no nodes.");
        }
        return nodes.get(0);
    }
}
