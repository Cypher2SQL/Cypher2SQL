package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.query.cypher.Pattern;
import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Mapping {
    private final SchemaDefinition schema;

    public Mapping(final SchemaDefinition schema) {
        this.schema = schema;
    }

    public SelectQuery toSql(final Query query) {
        final List<Pattern> patterns = query.patterns();
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("No patterns parsed from Cypher query.");
        }

        final Pattern pattern = patterns.get(0);
        final List<Node> nodes = pattern.nodes();
        final List<Edge> edges = pattern.edges();
        new TranslationCapabilities(query.raw(), edges.size()).ensureSupported();
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Cypher pattern contains no nodes.");
        }

        final Map<String, String> nodeAliases = assignNodeAliases(nodes);
        final AliasState aliases = new AliasState(nodeAliases.size());

        final Node root = nodes.get(0);
        final NodeMapping rootMapping = schema.nodeForLabel(root.label());
        final String rootAlias = nodeAliases.get(root.variable());
        final SelectQuery select = SelectQuery.from(rootMapping.table(), rootAlias);
        new Projection(query.returnItems(), rootAlias, nodeAliases).applyTo(select);

        for (int i = 0; i < edges.size(); i++) {
            final Edge edge = edges.get(i);
            final Node left = nodes.get(i);
            final Node right = nodes.get(i + 1);
            final EdgeMapping edgeMapping = schema.edgeForType(edge.type());
            final Relation relation = Relation.from(
                    edgeMapping,
                    left,
                    right,
                    nodeAliases.get(left.variable()),
                    nodeAliases.get(right.variable()));
            relation.applyTo(select, schema, aliases);
        }

        return select;
    }

    private Map<String, String> assignNodeAliases(final List<Node> nodes) {
        final Map<String, String> nodeAliases = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            nodeAliases.put(nodes.get(i).variable(), "t" + i);
        }
        return nodeAliases;
    }
}
