package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.Pattern;
import com.iisaka.cypher2sql.query.cypher.ReturnItem;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

import java.util.List;
import java.util.Map;

final class Projection {
    private final Pattern pattern;
    private final List<ReturnItem> returnItems;
    private final Map<String, EdgeProjection> edgeProjections;

    Projection(
            final Pattern pattern,
            final List<ReturnItem> returnItems,
            final Map<String, EdgeProjection> edgeProjections) {
        this.pattern = pattern;
        this.returnItems = returnItems;
        this.edgeProjections = edgeProjections;
    }

    void applyTo(final SelectQuery select) {
        if (returnItems.isEmpty()) {
            select.addSelectColumn(pattern.rootAlias() + ".*");
            return;
        }
        for (final ReturnItem item : returnItems) {
            final String alias = pattern.aliasForVariable(item.variable());
            if (alias != null) {
                if (item.property() == null) {
                    select.addSelectColumn(alias + ".*");
                } else {
                    select.addSelectColumn(alias + "." + item.property());
                }
                continue;
            }

            final EdgeProjection edgeProjection = edgeProjections.get(item.variable());
            if (edgeProjection != null) {
                if (item.property() != null) {
                    throw new IllegalArgumentException(
                            "RETURN edge properties are not supported yet: " + item.variable() + "." + item.property());
                }
                for (final String column : edgeProjection.columns()) {
                    select.addSelectColumn(column);
                }
                continue;
            }

            throw new IllegalArgumentException("RETURN references unknown variable: " + item.variable());
        }
    }
}
