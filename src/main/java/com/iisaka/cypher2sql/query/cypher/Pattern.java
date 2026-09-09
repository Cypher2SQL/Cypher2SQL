package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.Expression;
import com.iisaka.cypher2sql.query.read.BoundNode;
import com.iisaka.cypher2sql.query.read.BoundPattern;
import com.iisaka.cypher2sql.query.read.BoundTraversal;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record Pattern(List<Node> nodes, List<Edge> edges) {

    public BoundPattern bind(
            final SchemaDefinition schema,
            final Map<String, BoundNode> boundByVariable,
            final int[] nextAliasIndex,
            final boolean optional,
            final Expression whereExpression) {
        final List<Node> resolvedNodes = resolveNodeLabels(schema, substituteBoundLabels(boundByVariable));
        if (resolvedNodes.isEmpty()) {
            throw new IllegalArgumentException("Cypher pattern contains no nodes.");
        }

        final List<BoundNode> boundNodes = new ArrayList<>();
        for (final Node node : resolvedNodes) {
            final BoundNode existing = node.variable() == null || node.variable().isBlank()
                    ? null
                    : boundByVariable.get(node.variable());
            final BoundNode boundNode = existing != null
                    ? existing
                    : new BoundNode(node, schema.nodeForLabel(node.label()), "t" + nextAliasIndex[0]++);
            if (existing == null && node.variable() != null && !node.variable().isBlank()) {
                boundByVariable.put(node.variable(), boundNode);
            }
            boundNodes.add(boundNode);
        }

        final List<BoundTraversal> traversals = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            traversals.add(new BoundTraversal(
                    edges.get(i),
                    resolveRelation(schema, edges.get(i), resolvedNodes.get(i), resolvedNodes.get(i + 1)),
                    boundNodes.get(i),
                    boundNodes.get(i + 1)));
        }
        return new BoundPattern(boundNodes, traversals, optional, whereExpression);
    }

    private List<Node> substituteBoundLabels(final Map<String, BoundNode> boundByVariable) {
        final List<Node> substituted = new ArrayList<>(nodes.size());
        for (final Node node : nodes) {
            final boolean needsLabel = node.label() == null || node.label().isBlank();
            final BoundNode existing = node.variable() == null || node.variable().isBlank()
                    ? null
                    : boundByVariable.get(node.variable());
            substituted.add(needsLabel && existing != null ? new Node(node.variable(), existing.label()) : node);
        }
        return substituted;
    }

    private List<Node> resolveNodeLabels(final SchemaDefinition schema, final List<Node> substitutedNodes) {
        final List<Node> resolved = new ArrayList<>();
        final List<EdgeMapping> edgeMappings = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            edgeMappings.add(resolveEdgeMappingForInference(schema, edges.get(i), substitutedNodes.get(i), substitutedNodes.get(i + 1)));
        }
        for (int i = 0; i < substitutedNodes.size(); i++) {
            final Node node = substitutedNodes.get(i);
            if (node.label() != null && !node.label().isBlank()) {
                resolved.add(node);
                continue;
            }
            String inferredLabel = null;
            if (i > 0) {
                final Edge previousEdge = edges.get(i - 1);
                final EdgeMapping previousMapping = edgeMappings.get(i - 1);
                final String candidate = previousEdge.direction() == Edge.Direction.RIGHT_TO_LEFT
                        ? previousMapping.fromLabel()
                        : previousMapping.toLabel();
                inferredLabel = mergeLabel(inferredLabel, candidate, i);
            }
            if (i < edgeMappings.size()) {
                final Edge nextEdge = edges.get(i);
                final EdgeMapping nextMapping = edgeMappings.get(i);
                final String candidate = nextEdge.direction() == Edge.Direction.RIGHT_TO_LEFT
                        ? nextMapping.toLabel()
                        : nextMapping.fromLabel();
                inferredLabel = mergeLabel(inferredLabel, candidate, i);
            }
            resolved.add(new Node(node.variable(), inferredLabel));
        }
        return resolved;
    }

    private EdgeMapping resolveEdgeMappingForInference(
            final SchemaDefinition schema,
            final Edge edge,
            final Node left,
            final Node right) {
        final boolean hasLeft = left.label() != null && !left.label().isBlank();
        final boolean hasRight = right.label() != null && !right.label().isBlank();
        if (!hasLeft || !hasRight) {
            return schema.edgeForType(edge.type());
        }
        return switch (edge.direction()) {
            case LEFT_TO_RIGHT -> edgeForDirectedLabelsOrFallback(schema, edge.type(), left.label(), right.label());
            case RIGHT_TO_LEFT -> edgeForDirectedLabelsOrFallback(schema, edge.type(), right.label(), left.label());
            case UNDIRECTED -> schema.edgeForTypeUndirected(edge.type(), left.label(), right.label());
        };
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

    private EdgeMapping resolveRelation(
            final SchemaDefinition schema,
            final Edge edge,
            final Node left,
            final Node right) {
        return switch (edge.direction()) {
            case LEFT_TO_RIGHT -> edgeForDirectedLabelsOrFallback(schema, edge.type(), left.label(), right.label());
            case RIGHT_TO_LEFT -> edgeForDirectedLabelsOrFallback(schema, edge.type(), right.label(), left.label());
            case UNDIRECTED -> schema.edgeForTypeUndirected(edge.type(), left.label(), right.label());
        };
    }

    private EdgeMapping edgeForDirectedLabelsOrFallback(
            final SchemaDefinition schema,
            final String type,
            final String fromLabel,
            final String toLabel) {
        try {
            return schema.edgeForType(type, fromLabel, toLabel);
        } catch (final IllegalArgumentException missingDirected) {
            try {
                return schema.edgeForType(type);
            } catch (final IllegalArgumentException noUniqueByType) {
                throw new IllegalArgumentException("Edge mapping labels do not match nodes: " + type, missingDirected);
            }
        }
    }
}
