package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.ReturnItem;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

import java.util.List;
import java.util.Map;

final class Projection {
    private final List<ReturnItem> returnItems;
    private final String rootAlias;
    private final Map<String, String> nodeAliases;

    Projection(final List<ReturnItem> returnItems, final String rootAlias, final Map<String, String> nodeAliases) {
        this.returnItems = returnItems;
        this.rootAlias = rootAlias;
        this.nodeAliases = nodeAliases;
    }

    void applyTo(final SelectQuery select) {
        if (returnItems.isEmpty()) {
            select.addSelectColumn(rootAlias + ".*");
            return;
        }
        for (final ReturnItem item : returnItems) {
            final String alias = nodeAliases.get(item.variable());
            if (alias == null) {
                throw new IllegalArgumentException("RETURN references unknown variable: " + item.variable());
            }
            if (item.property() == null) {
                select.addSelectColumn(alias + ".*");
            } else {
                select.addSelectColumn(alias + "." + item.property());
            }
        }
    }
}
