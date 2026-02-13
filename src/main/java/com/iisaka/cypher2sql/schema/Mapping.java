package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.query.cypher.Pattern;
import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

import java.util.ArrayList;
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
        final List<Edge> edges = pattern.edges();
        new TranslationCapabilities(query.raw()).ensureSupported();
        final List<Node> nodes = resolveNodeLabels(pattern);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Cypher pattern contains no nodes.");
        }

        final AliasState aliases = new AliasState(nodes.size());

        final Node root = nodes.get(0);
        final NodeMapping rootMapping = schema.nodeForLabel(root.label());
        final SelectQuery select = SelectQuery.from(rootMapping.table(), pattern.rootAlias());
        final Map<String, EdgeProjection> edgeProjections = new HashMap<>();

        for (int i = 0; i < edges.size(); i++) {
            final Edge edge = pattern.edgeAt(i);
            final Node left = nodes.get(i);
            final Node right = nodes.get(i + 1);
            final EdgeMapping edgeMapping = schema.edgeForType(edge.type());
            final Relation relation = Relation.from(
                    edgeMapping,
                    left,
                    right,
                    pattern.aliasAt(i),
                    pattern.aliasAt(i + 1));
            final EdgeProjection projection = relation.applyTo(select, schema, aliases);
            if (edge.variable() != null && !edge.variable().isBlank()) {
                edgeProjections.put(edge.variable(), projection);
            }
        }
        new Projection(pattern, query.returnItems(), edgeProjections).applyTo(select);

        return select;
    }

    private List<Node> resolveNodeLabels(final Pattern pattern) {
        final List<Node> resolved = new ArrayList<>();
        final List<Edge> edges = pattern.edges();
        final List<EdgeMapping> edgeMappings = new ArrayList<>();
        for (final Edge edge : edges) {
            edgeMappings.add(schema.edgeForType(edge.type()));
        }
        for (int i = 0; i < pattern.nodes().size(); i++) {
            final Node node = pattern.nodeAt(i);
            if (node.label() != null && !node.label().isBlank()) {
                resolved.add(node);
                continue;
            }
            String inferredLabel = null;
            if (i > 0) {
                inferredLabel = mergeLabel(inferredLabel, edgeMappings.get(i - 1).toLabel(), i);
            }
            if (i < edgeMappings.size()) {
                inferredLabel = mergeLabel(inferredLabel, edgeMappings.get(i).fromLabel(), i);
            }
            resolved.add(new Node(node.variable(), inferredLabel));
        }
        return resolved;
    }

    private String mergeLabel(final String current, final String candidate, final int nodeIndex) {
        if (candidate == null || candidate.isBlank()) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        if (!current.equals(candidate)) {
            throw new IllegalArgumentException("Unable to infer unique label for anonymous node at index " + nodeIndex);
        }
        return current;
    }
}
