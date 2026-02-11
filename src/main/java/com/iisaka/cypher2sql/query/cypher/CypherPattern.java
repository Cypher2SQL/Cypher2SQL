package com.iisaka.cypher2sql.query.cypher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CypherPattern {
    private final List<CypherNode> nodes;
    private final List<CypherEdge> edges;

    public CypherPattern(final List<CypherNode> nodes, final List<CypherEdge> edges) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
    }

    public List<CypherNode> nodes() {
        return nodes;
    }

    public List<CypherEdge> edges() {
        return edges;
    }
}
