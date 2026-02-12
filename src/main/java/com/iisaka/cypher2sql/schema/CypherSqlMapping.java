package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.CypherEdge;
import com.iisaka.cypher2sql.query.cypher.CypherNode;
import com.iisaka.cypher2sql.query.cypher.CypherPattern;
import com.iisaka.cypher2sql.query.cypher.CypherQuery;
import com.iisaka.cypher2sql.query.cypher.CypherReturnItem;
import com.iisaka.cypher2sql.query.sql.SqlJoin;
import com.iisaka.cypher2sql.query.sql.SqlSelect;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CypherSqlMapping {
    private final SchemaDefinition schema;

    public CypherSqlMapping(final SchemaDefinition schema) {
        this.schema = schema;
    }

    public SqlSelect toSql(final CypherQuery query) {
        if (containsVariableLengthTraversal(query.raw())) {
            return translateVariableLengthTraversal(query);
        }

        final List<CypherPattern> patterns = query.patterns();
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("No patterns parsed from Cypher query.");
        }

        final CypherPattern pattern = patterns.get(0);
        final List<CypherNode> nodes = pattern.nodes();
        final List<CypherEdge> edges = pattern.edges();
        if (edges.size() > 1) {
            return translateMultiHopTraversal(query);
        }
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Cypher pattern contains no nodes.");
        }

        final AtomicInteger aliasCounter = new AtomicInteger();
        final Map<String, String> nodeAliases = new HashMap<>();
        for (final CypherNode node : nodes) {
            nodeAliases.put(node.variable(), "t" + aliasCounter.getAndIncrement());
        }

        final CypherNode root = nodes.get(0);
        final NodeMapping rootMapping = schema.nodeForLabel(root.label());
        final String rootAlias = nodeAliases.get(root.variable());
        final SqlSelect select = SqlSelect.from(rootMapping.table(), rootAlias);
        applyReturnProjection(select, query.returnItems(), rootAlias, nodeAliases);

        for (int i = 0; i < edges.size(); i++) {
            final CypherEdge edge = edges.get(i);
            final CypherNode left = nodes.get(i);
            final CypherNode right = nodes.get(i + 1);
            final EdgeMapping edgeMapping = schema.edgeForType(edge.type());
            applyEdge(select, edgeMapping, left, right, nodeAliases, aliasCounter);
        }

        return select;
    }

    private void applyReturnProjection(
            final SqlSelect select,
            final List<CypherReturnItem> returnItems,
            final String rootAlias,
            final Map<String, String> nodeAliases) {
        if (returnItems.isEmpty()) {
            select.addSelectColumn(rootAlias + ".*");
            return;
        }
        for (final CypherReturnItem item : returnItems) {
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

    private boolean containsVariableLengthTraversal(final String rawCypher) {
        return rawCypher != null && rawCypher.contains("[*");
    }

    private SqlSelect translateVariableLengthTraversal(final CypherQuery query) {
        // Placeholder only: recursive traversal translation is intentionally not implemented yet.
        throw new UnsupportedOperationException(
                "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.");
    }

    private SqlSelect translateMultiHopTraversal(final CypherQuery query) {
        // Placeholder only: multi-hop traversal planning is intentionally not implemented yet.
        throw new UnsupportedOperationException(
                "Multi-hop traversals are not supported yet; traversal planning is a future enhancement.");
    }

    private void applyEdge(
            final SqlSelect select,
            final EdgeMapping edgeMapping,
            final CypherNode left,
            final CypherNode right,
            final Map<String, String> nodeAliases,
            final AtomicInteger aliasCounter) {
        final NodeMapping leftMapping = schema.nodeForLabel(left.label());
        final NodeMapping rightMapping = schema.nodeForLabel(right.label());
        final String leftAlias = nodeAliases.get(left.variable());
        final String rightAlias = nodeAliases.get(right.variable());

        switch (edgeMapping.relationshipKind()) {
            case JOIN_TABLE -> {
                final String joinAlias = "j" + aliasCounter.getAndIncrement();
                final String joinOnLeft = leftAlias + "." + leftMapping.primaryKey()
                        + " = " + joinAlias + "." + edgeMapping.fromJoinKey();
                select.addJoin(new SqlJoin(SqlJoin.JoinType.INNER, edgeMapping.joinTable(), joinAlias, joinOnLeft));

                final String joinOnRight = joinAlias + "." + edgeMapping.toJoinKey()
                        + " = " + rightAlias + "." + rightMapping.primaryKey();
                select.addJoin(new SqlJoin(SqlJoin.JoinType.INNER, rightMapping.table(), rightAlias, joinOnRight));
            }
            case SELF_REFERENTIAL -> {
                final String joinOnSelf = leftAlias + "." + edgeMapping.fromKey()
                        + " = " + rightAlias + "." + edgeMapping.toKey();
                select.addJoin(new SqlJoin(SqlJoin.JoinType.INNER, leftMapping.table(), rightAlias, joinOnSelf));
            }
            case ONE_TO_MANY -> {
                final String parentLabel = edgeMapping.fromLabel();
                final String childLabel = edgeMapping.toLabel();
                final boolean leftIsParent = left.label().equals(parentLabel) && right.label().equals(childLabel);
                final boolean rightIsParent = right.label().equals(parentLabel) && left.label().equals(childLabel);
                if (leftIsParent) {
                    final String joinOn = rightAlias + "." + edgeMapping.childForeignKey()
                            + " = " + leftAlias + "." + edgeMapping.parentPrimaryKey();
                    select.addJoin(new SqlJoin(SqlJoin.JoinType.INNER, rightMapping.table(), rightAlias, joinOn));
                } else if (rightIsParent) {
                    final String joinOn = leftAlias + "." + edgeMapping.childForeignKey()
                            + " = " + rightAlias + "." + edgeMapping.parentPrimaryKey();
                    select.addJoin(new SqlJoin(SqlJoin.JoinType.INNER, rightMapping.table(), rightAlias, joinOn));
                } else {
                    throw new IllegalArgumentException("Edge mapping labels do not match nodes: " + edgeMapping.type());
                }
            }
        }
    }
}
