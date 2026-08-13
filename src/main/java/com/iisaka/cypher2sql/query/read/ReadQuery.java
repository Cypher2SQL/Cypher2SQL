package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.ProjectionItem;
import com.iisaka.cypher2sql.query.cypher.Expression;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReadQuery {
    private final List<BoundPattern> patterns;
    private final Expression whereExpression;
    private final List<ProjectionItem> projectionItems;

    public ReadQuery(
            final List<BoundPattern> patterns,
            final Expression whereExpression,
            final List<ProjectionItem> projectionItems) {
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.whereExpression = whereExpression;
        this.projectionItems = Collections.unmodifiableList(new ArrayList<>(projectionItems));
    }

    public int patternCount() {
        return patterns.size();
    }

    public BoundPattern patternAt(final int index) {
        return patterns.get(index);
    }

    public List<ProjectionItem> projectionItems() {
        return projectionItems;
    }

    public Expression whereExpression() {
        return whereExpression;
    }

    public SelectQuery asSql() {
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("No patterns parsed from Cypher query.");
        }
        if (patterns.size() > 1) {
            throw new UnsupportedOperationException(
                    "Multiple top-level MATCH patterns are not supported yet. Found: " + patterns.size());
        }
        return patterns.get(0).asSql(whereExpression, projectionItems);
    }
}
