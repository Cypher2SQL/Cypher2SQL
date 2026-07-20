package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.read.BoundNode;
import com.iisaka.cypher2sql.query.read.BoundPattern;
import com.iisaka.cypher2sql.query.read.BoundTraversal;
import com.iisaka.cypher2sql.query.read.ReadQuery;
import com.iisaka.cypher2sql.query.sql.SelectQuery;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Query {
    private final String raw;
    private final ParseTree parseTree;
    private final List<List<Node>> patternNodes;
    private final List<List<Edge>> patternEdges;
    private final List<Map<String, String>> aliasesByVariable;
    private final List<ProjectionItem> withProjectionItems;
    private final Expression withWhereExpression;
    private final Expression whereExpression;
    private final List<ProjectionItem> projectionItems;
    private final boolean hasVariableLengthTraversal;

    private Query(
            final String raw,
            final ParseTree parseTree,
            final List<List<Node>> patternNodes,
            final List<List<Edge>> patternEdges,
            final List<ProjectionItem> withProjectionItems,
            final Expression withWhereExpression,
            final Expression whereExpression,
            final List<ProjectionItem> projectionItems,
            final boolean hasVariableLengthTraversal) {
        this.raw = raw;
        this.parseTree = parseTree;
        this.patternNodes = copyNested(patternNodes);
        this.patternEdges = copyNested(patternEdges);
        this.aliasesByVariable = buildAliasMaps(this.patternNodes);
        this.withProjectionItems = Collections.unmodifiableList(new ArrayList<>(withProjectionItems));
        this.withWhereExpression = withWhereExpression;
        this.whereExpression = whereExpression;
        this.projectionItems = Collections.unmodifiableList(new ArrayList<>(projectionItems));
        this.hasVariableLengthTraversal = hasVariableLengthTraversal;
    }

    public String raw() {
        return raw;
    }

    public int patternCount() {
        return patternNodes.size();
    }

    public List<Node> nodesAt(final int patternIndex) {
        return patternNodes.get(patternIndex);
    }

    public List<Edge> edgesAt(final int patternIndex) {
        return patternEdges.get(patternIndex);
    }

    public String aliasAt(final int patternIndex, final int nodeIndex) {
        return "t" + nodeIndex;
    }

    public String rootAliasAt(final int patternIndex) {
        return aliasAt(patternIndex, 0);
    }

    public String aliasForVariable(final int patternIndex, final String variable) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        return aliasesByVariable.get(patternIndex).get(variable);
    }

    public List<ProjectionItem> projectionItems() {
        return projectionItems;
    }

    public Expression whereExpression() {
        return whereExpression;
    }

    public boolean hasWithClause() {
        return !withProjectionItems.isEmpty() || withWhereExpression != null;
    }

    public List<ProjectionItem> withProjectionItems() {
        return withProjectionItems;
    }

    public Expression withWhereExpression() {
        return withWhereExpression;
    }

    public ParseTree parseTree() {
        return parseTree;
    }

    public boolean hasVariableLengthTraversal() {
        return hasVariableLengthTraversal;
    }

    public static Query of(final String cypher) {
        final Syntax.ParsedCypher parsed = Syntax.cypher25().parse(cypher);
        final ParseTree parseTree = parsed.parseTree();
        final String[] ruleNames = parsed.ruleNames();
        final List<ExtractedPattern> patterns = extractPatterns(parseTree, ruleNames);
        return new Query(
                cypher,
                parseTree,
                patterns.stream().map(ExtractedPattern::nodes).toList(),
                patterns.stream().map(ExtractedPattern::edges).toList(),
                extractWithProjectionItems(parseTree),
                extractWithWhereExpression(parseTree),
                extractWhereExpression(parseTree),
                extractProjectionItems(parseTree),
                hasVariableLengthTraversal(parseTree, ruleNames));
    }

    public SelectQuery asSql(final SchemaDefinition schema) {
        return asReadQuery(schema).asSql();
    }

    public ReadQuery asReadQuery(final SchemaDefinition schema) {
        if (hasVariableLengthTraversal()) {
            // Placeholder only: recursive traversal translation is intentionally not implemented yet.
            throw new UnsupportedOperationException(
                    "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.");
        }
        if (hasWithClause()) {
            throw new UnsupportedOperationException(
                    "WITH clauses are parsed but not rendered yet; pipeline semantics are a future enhancement.");
        }

        final List<BoundPattern> patterns = new ArrayList<>();
        for (int patternIndex = 0; patternIndex < patternCount(); patternIndex++) {
            final List<Edge> edges = edgesAt(patternIndex);
            final List<Node> resolvedNodes = resolveNodeLabels(schema, nodesAt(patternIndex), edges);
            if (resolvedNodes.isEmpty()) {
                throw new IllegalArgumentException("Cypher pattern contains no nodes.");
            }

            final List<BoundNode> boundNodes = new ArrayList<>();
            for (int nodeIndex = 0; nodeIndex < resolvedNodes.size(); nodeIndex++) {
                final Node node = resolvedNodes.get(nodeIndex);
                boundNodes.add(new BoundNode(
                        node,
                        schema.nodeForLabel(node.label()),
                        aliasAt(patternIndex, nodeIndex)));
            }

            final List<BoundTraversal> traversals = new ArrayList<>();
            for (int i = 0; i < edges.size(); i++) {
                traversals.add(new BoundTraversal(
                        edges.get(i),
                        resolveRelation(schema, edges.get(i), resolvedNodes.get(i), resolvedNodes.get(i + 1)),
                        boundNodes.get(i),
                        boundNodes.get(i + 1)));
            }
            patterns.add(new BoundPattern(boundNodes, traversals));
        }
        return new ReadQuery(patterns, whereExpression, projectionItems);
    }

    private static List<ExtractedPattern> extractPatterns(final ParseTree parseTree, final String[] ruleNames) {
        final List<ParserRuleContext> patternRoots = findPatternElements(parseTree, ruleNames);
        if (patternRoots.isEmpty()) {
            return List.of();
        }

        final List<ExtractedPattern> extracted = new ArrayList<>();
        for (final ParserRuleContext patternRoot : patternRoots) {
            final List<ParserRuleContext> nodeContexts = new ArrayList<>();
            final List<ParserRuleContext> relationshipContexts = new ArrayList<>();
            final Deque<ParseTree> stack = new ArrayDeque<>();
            stack.push(patternRoot);
            while (!stack.isEmpty()) {
                final ParseTree current = stack.pop();
                if (current instanceof ParserRuleContext context) {
                    final String ruleName = ruleNames[context.getRuleIndex()];
                    if ("nodePattern".equals(ruleName)) {
                        nodeContexts.add(context);
                    } else if ("relationshipPattern".equals(ruleName)) {
                        relationshipContexts.add(context);
                    }
                }
                for (int i = current.getChildCount() - 1; i >= 0; i--) {
                    stack.push(current.getChild(i));
                }
            }

            if (nodeContexts.isEmpty()) {
                continue;
            }

            final List<Node> nodes = new ArrayList<>();
            for (final ParserRuleContext nodeContext : nodeContexts) {
                nodes.add(Node.fromPatternText(nodeContext.getText()));
            }

            final List<Edge> edges = new ArrayList<>();
            for (final ParserRuleContext relationshipContext : relationshipContexts) {
                edges.add(Edge.fromPatternText(relationshipContext.getText()));
            }
            extracted.add(new ExtractedPattern(nodes, edges));
        }

        return extracted;
    }

    private static List<ParserRuleContext> findPatternElements(final ParseTree parseTree, final String[] ruleNames) {
        final List<ParserRuleContext> roots = new ArrayList<>();
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleNames[context.getRuleIndex()];
                if ("patternElement".equals(ruleName)) {
                    roots.add(context);
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
        return roots;
    }

    private static List<ProjectionItem> extractProjectionItems(final ParseTree parseTree) {
        final Cypher25Parser.ReturnClauseContext returnClause = findFirst(parseTree, Cypher25Parser.ReturnClauseContext.class);
        if (returnClause == null || returnClause.returnBody() == null || returnClause.returnBody().returnItems() == null) {
            return List.of();
        }
        return projectionItems(returnClause.returnBody().returnItems());
    }

    private static List<ProjectionItem> extractWithProjectionItems(final ParseTree parseTree) {
        final Cypher25Parser.WithClauseContext withClause = findFirst(parseTree, Cypher25Parser.WithClauseContext.class);
        if (withClause == null || withClause.returnBody() == null || withClause.returnBody().returnItems() == null) {
            return List.of();
        }
        return projectionItems(withClause.returnBody().returnItems());
    }

    private static List<ProjectionItem> projectionItems(final Cypher25Parser.ReturnItemsContext returnItems) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(returnItems);
        final List<Cypher25Parser.ReturnItemContext> returnItemContexts = new ArrayList<>();
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof Cypher25Parser.ReturnItemContext context) {
                returnItemContexts.add(context);
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }

        final List<ProjectionItem> items = new ArrayList<>();
        for (final Cypher25Parser.ReturnItemContext context : returnItemContexts) {
            items.add(ProjectionItem.fromExpression(
                    Expression.parse(context.expression()),
                    context.variable() == null ? null : context.variable().getText()));
        }
        return items;
    }

    private static Expression extractWhereExpression(final ParseTree parseTree) {
        final List<Cypher25Parser.WhereClauseContext> whereClauses = findAll(parseTree, Cypher25Parser.WhereClauseContext.class);
        for (final Cypher25Parser.WhereClauseContext whereClause : whereClauses) {
            if (!isWithin(whereClause, Cypher25Parser.WithClauseContext.class)) {
                return Expression.parse(whereClause.expression());
            }
        }
        return null;
    }

    private static Expression extractWithWhereExpression(final ParseTree parseTree) {
        final List<Cypher25Parser.WhereClauseContext> whereClauses = findAll(parseTree, Cypher25Parser.WhereClauseContext.class);
        for (final Cypher25Parser.WhereClauseContext whereClause : whereClauses) {
            if (isWithin(whereClause, Cypher25Parser.WithClauseContext.class)) {
                return Expression.parse(whereClause.expression());
            }
        }
        return null;
    }

    private static <T extends ParseTree> T findFirst(final ParseTree parseTree, final Class<T> type) {
        final List<T> all = findAll(parseTree, type);
        return all.isEmpty() ? null : all.get(0);
    }

    private static <T extends ParseTree> List<T> findAll(final ParseTree parseTree, final Class<T> type) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        final List<T> matches = new ArrayList<>();
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (type.isInstance(current)) {
                matches.add(type.cast(current));
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
        return matches;
    }

    private static boolean isWithin(final ParseTree tree, final Class<? extends ParseTree> type) {
        ParseTree current = tree.getParent();
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean hasVariableLengthTraversal(final ParseTree parseTree, final String[] ruleNames) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleNames[context.getRuleIndex()];
                if ("relationshipPattern".equals(ruleName) && context.getText().contains("*")) {
                    return true;
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
        return false;
    }

    private List<Node> resolveNodeLabels(
            final SchemaDefinition schema,
            final List<Node> nodes,
            final List<Edge> edges) {
        final List<Node> resolved = new ArrayList<>();
        final List<EdgeMapping> edgeMappings = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            edgeMappings.add(resolveEdgeMappingForInference(schema, edges.get(i), nodes.get(i), nodes.get(i + 1)));
        }
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
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

    private static <T> List<List<T>> copyNested(final List<List<T>> nested) {
        final List<List<T>> copied = new ArrayList<>(nested.size());
        for (final List<T> list : nested) {
            copied.add(Collections.unmodifiableList(new ArrayList<>(list)));
        }
        return Collections.unmodifiableList(copied);
    }

    private static List<Map<String, String>> buildAliasMaps(final List<List<Node>> patternNodes) {
        final List<Map<String, String>> maps = new ArrayList<>(patternNodes.size());
        for (final List<Node> nodes : patternNodes) {
            final Map<String, String> aliases = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++) {
                final Node node = nodes.get(i);
                if (node.variable() != null && !node.variable().isBlank()) {
                    aliases.put(node.variable(), "t" + i);
                }
            }
            maps.add(Collections.unmodifiableMap(aliases));
        }
        return Collections.unmodifiableList(maps);
    }

    private record ExtractedPattern(List<Node> nodes, List<Edge> edges) {
    }
}
