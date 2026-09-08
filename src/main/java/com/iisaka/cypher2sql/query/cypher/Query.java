package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.read.BoundNode;
import com.iisaka.cypher2sql.query.read.BoundPattern;
import com.iisaka.cypher2sql.query.read.BoundTraversal;
import com.iisaka.cypher2sql.query.read.ReadQuery;
import com.iisaka.cypher2sql.query.sql.SelectQuery;
import com.iisaka.cypher2sql.schema.EdgeMapping;
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
import java.util.Set;

public final class Query {
    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of("count", "sum", "avg", "min", "max");

    private final String raw;
    private final ParseTree parseTree;
    private final List<List<Node>> patternNodes;
    private final List<List<Edge>> patternEdges;
    private final List<Boolean> patternOptional;
    private final List<Expression> patternLocalWhereExpressions;
    private final List<ProjectionItem> withProjectionItems;
    private final boolean withDistinct;
    private final List<OrderItem> withOrderItems;
    private final Long withSkip;
    private final Long withLimit;
    private final Expression withWhereExpression;
    private final Expression whereExpression;
    private final List<ProjectionItem> projectionItems;
    private final boolean distinct;
    private final List<OrderItem> orderItems;
    private final Long skip;
    private final Long limit;
    private final boolean hasVariableLengthTraversal;
    private final boolean hasMultipleWithClauses;
    private final boolean hasMatchAfterWith;

    private Query(
            final String raw,
            final ParseTree parseTree,
            final List<List<Node>> patternNodes,
            final List<List<Edge>> patternEdges,
            final List<Boolean> patternOptional,
            final List<Expression> patternLocalWhereExpressions,
            final List<ProjectionItem> withProjectionItems,
            final boolean withDistinct,
            final List<OrderItem> withOrderItems,
            final Long withSkip,
            final Long withLimit,
            final Expression withWhereExpression,
            final List<ProjectionItem> projectionItems,
            final boolean distinct,
            final List<OrderItem> orderItems,
            final Long skip,
            final Long limit,
            final boolean hasVariableLengthTraversal,
            final boolean hasMultipleWithClauses,
            final boolean hasMatchAfterWith) {
        this.raw = raw;
        this.parseTree = parseTree;
        this.patternNodes = copyNested(patternNodes);
        this.patternEdges = copyNested(patternEdges);
        this.patternOptional = Collections.unmodifiableList(new ArrayList<>(patternOptional));
        this.patternLocalWhereExpressions = Collections.unmodifiableList(new ArrayList<>(patternLocalWhereExpressions));
        this.withProjectionItems = Collections.unmodifiableList(new ArrayList<>(withProjectionItems));
        this.withDistinct = withDistinct;
        this.withOrderItems = Collections.unmodifiableList(new ArrayList<>(withOrderItems));
        this.withSkip = withSkip;
        this.withLimit = withLimit;
        this.withWhereExpression = withWhereExpression;
        this.whereExpression = patternLocalWhereExpressions.isEmpty() ? null : patternLocalWhereExpressions.get(0);
        this.projectionItems = Collections.unmodifiableList(new ArrayList<>(projectionItems));
        this.distinct = distinct;
        this.orderItems = Collections.unmodifiableList(new ArrayList<>(orderItems));
        this.skip = skip;
        this.limit = limit;
        this.hasVariableLengthTraversal = hasVariableLengthTraversal;
        this.hasMultipleWithClauses = hasMultipleWithClauses;
        this.hasMatchAfterWith = hasMatchAfterWith;
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

    public boolean isPatternOptional(final int patternIndex) {
        return patternOptional.get(patternIndex);
    }

    public List<ProjectionItem> projectionItems() {
        return projectionItems;
    }

    public boolean distinct() {
        return distinct;
    }

    public Expression whereExpression() {
        return whereExpression;
    }

    public List<OrderItem> orderItems() {
        return orderItems;
    }

    public Long skip() {
        return skip;
    }

    public Long limit() {
        return limit;
    }

    public boolean hasWithClause() {
        return !withProjectionItems.isEmpty() || withWhereExpression != null;
    }

    public List<ProjectionItem> withProjectionItems() {
        return withProjectionItems;
    }

    public boolean withDistinct() {
        return withDistinct;
    }

    public List<OrderItem> withOrderItems() {
        return withOrderItems;
    }

    public Long withSkip() {
        return withSkip;
    }

    public Long withLimit() {
        return withLimit;
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
        final Cypher25Parser.ReturnBodyContext returnBody = returnBody(parseTree);
        final Cypher25Parser.WithClauseContext withClause = withClause(parseTree);
        final Cypher25Parser.ReturnBodyContext withReturnBody = withClause == null ? null : withClause.returnBody();
        return new Query(
                cypher,
                parseTree,
                patterns.stream().map(ExtractedPattern::nodes).toList(),
                patterns.stream().map(ExtractedPattern::edges).toList(),
                patterns.stream().map(ExtractedPattern::optional).toList(),
                patterns.stream().map(ExtractedPattern::localWhereExpression).toList(),
                extractWithProjectionItems(withClause),
                extractDistinct(withReturnBody),
                extractOrderItems(withReturnBody),
                extractSkip(withReturnBody),
                extractLimit(withReturnBody),
                extractWithWhereExpression(withClause),
                extractProjectionItems(parseTree),
                extractDistinct(returnBody),
                extractOrderItems(returnBody),
                extractSkip(returnBody),
                extractLimit(returnBody),
                hasVariableLengthTraversal(parseTree, ruleNames),
                hasMultipleWithClauses(parseTree),
                hasMatchAfterWith(parseTree));
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
            if (hasMultipleWithClauses) {
                throw new UnsupportedOperationException("Only one WITH clause is supported yet.");
            }
            if (hasMatchAfterWith) {
                throw new UnsupportedOperationException("MATCH after WITH is not supported yet.");
            }
            if (hasMixedAggregation(withProjectionItems)) {
                throw new UnsupportedOperationException(
                        "Aggregation grouping in WITH is not supported yet; all WITH items must be aggregate expressions, or none.");
            }
        }

        final Map<String, BoundNode> boundByVariable = new HashMap<>();
        final int[] nextAliasIndex = {0};
        final List<BoundPattern> patterns = new ArrayList<>();
        for (int patternIndex = 0; patternIndex < patternCount(); patternIndex++) {
            final List<Edge> edges = edgesAt(patternIndex);
            final List<Node> resolvedNodes = resolveNodeLabels(
                    schema, substituteBoundLabels(nodesAt(patternIndex), boundByVariable), edges);
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
            patterns.add(new BoundPattern(
                    boundNodes, traversals, isPatternOptional(patternIndex), patternLocalWhereExpressions.get(patternIndex)));
        }

        if (!hasWithClause()) {
            return new ReadQuery(patterns, whereExpression, projectionItems, distinct, orderItems, skip, limit);
        }
        return new ReadQuery(
                patterns,
                whereExpression,
                withProjectionItems,
                withDistinct,
                withOrderItems,
                withSkip,
                withLimit,
                new ReadQuery.FinalStage(withWhereExpression, projectionItems, distinct, orderItems, skip, limit));
    }

    private static boolean isAggregate(final Expression expression) {
        return switch (expression) {
            case Expression.FunctionExpression function -> AGGREGATE_FUNCTIONS.contains(function.name().toLowerCase())
                    || function.arguments().stream().anyMatch(Query::isAggregate);
            case Expression.PropertyExpression property -> isAggregate(property.receiver());
            case Expression.BinaryExpression binary -> isAggregate(binary.left()) || isAggregate(binary.right());
            case Expression.UnaryExpression unary -> isAggregate(unary.operand());
            case Expression.CaseExpression caseExpression -> (caseExpression.subject() != null && isAggregate(caseExpression.subject()))
                    || caseExpression.whenThens().stream()
                            .anyMatch(whenThen -> isAggregate(whenThen.whenExpression()) || isAggregate(whenThen.thenExpression()))
                    || (caseExpression.elseExpression() != null && isAggregate(caseExpression.elseExpression()));
            default -> false;
        };
    }

    private static boolean hasMixedAggregation(final List<ProjectionItem> items) {
        boolean anyAggregate = false;
        boolean anyNonAggregate = false;
        for (final ProjectionItem item : items) {
            if (isAggregate(item.expression())) {
                anyAggregate = true;
            } else {
                anyNonAggregate = true;
            }
        }
        return anyAggregate && anyNonAggregate;
    }

    private static List<Node> substituteBoundLabels(final List<Node> nodes, final Map<String, BoundNode> boundByVariable) {
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

    private static List<ExtractedPattern> extractPatterns(final ParseTree parseTree, final String[] ruleNames) {
        final List<Cypher25Parser.MatchClauseContext> matchClauses = findAll(parseTree, Cypher25Parser.MatchClauseContext.class);
        final List<ExtractedPattern> extracted = new ArrayList<>();
        for (final Cypher25Parser.MatchClauseContext matchClause : matchClauses) {
            final boolean optional = matchClause.OPTIONAL() != null;
            final Expression localWhereExpression = matchClause.whereClause() == null
                    ? null
                    : Expression.parse(matchClause.whereClause().expression());
            final List<ParserRuleContext> patternRoots = findPatternElements(matchClause.patternList(), ruleNames);
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
                extracted.add(new ExtractedPattern(nodes, edges, optional, localWhereExpression));
            }
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

    private static Cypher25Parser.ReturnBodyContext returnBody(final ParseTree parseTree) {
        final Cypher25Parser.ReturnClauseContext returnClause = findFirst(parseTree, Cypher25Parser.ReturnClauseContext.class);
        return returnClause == null ? null : returnClause.returnBody();
    }

    private static Cypher25Parser.WithClauseContext withClause(final ParseTree parseTree) {
        return findFirst(parseTree, Cypher25Parser.WithClauseContext.class);
    }

    private static List<ProjectionItem> extractProjectionItems(final ParseTree parseTree) {
        final Cypher25Parser.ReturnBodyContext returnBody = returnBody(parseTree);
        if (returnBody == null || returnBody.returnItems() == null) {
            return List.of();
        }
        return projectionItems(returnBody.returnItems());
    }

    private static boolean extractDistinct(final Cypher25Parser.ReturnBodyContext returnBody) {
        return returnBody != null && returnBody.DISTINCT() != null;
    }

    private static List<OrderItem> extractOrderItems(final Cypher25Parser.ReturnBodyContext returnBody) {
        if (returnBody == null || returnBody.orderBy() == null) {
            return List.of();
        }
        final List<OrderItem> orderItems = new ArrayList<>();
        for (final Cypher25Parser.OrderItemContext orderItem : returnBody.orderBy().orderItem()) {
            orderItems.add(new OrderItem(Expression.parse(orderItem.expression()), orderItem.descToken() != null));
        }
        return orderItems;
    }

    private static Long extractSkip(final Cypher25Parser.ReturnBodyContext returnBody) {
        if (returnBody == null || returnBody.skip() == null) {
            return null;
        }
        return requireIntegerLiteral(Expression.parse(returnBody.skip().expression()), "SKIP");
    }

    private static Long extractLimit(final Cypher25Parser.ReturnBodyContext returnBody) {
        if (returnBody == null || returnBody.limit() == null) {
            return null;
        }
        return requireIntegerLiteral(Expression.parse(returnBody.limit().expression()), "LIMIT");
    }

    private static Long requireIntegerLiteral(final Expression expression, final String clause) {
        if (expression instanceof Expression.ConstantExpression constant && constant.value() instanceof Long value) {
            return value;
        }
        throw new UnsupportedOperationException(
                clause + " must be an integer literal; parameters are not supported yet.");
    }

    private static List<ProjectionItem> extractWithProjectionItems(final Cypher25Parser.WithClauseContext withClause) {
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

    private static Expression extractWithWhereExpression(final Cypher25Parser.WithClauseContext withClause) {
        if (withClause == null || withClause.whereClause() == null) {
            return null;
        }
        return Expression.parse(withClause.whereClause().expression());
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

    private static boolean hasMultipleWithClauses(final ParseTree parseTree) {
        return findAll(parseTree, Cypher25Parser.WithClauseContext.class).size() > 1;
    }

    private static boolean hasMatchAfterWith(final ParseTree parseTree) {
        final Cypher25Parser.WithClauseContext withClause = withClause(parseTree);
        if (withClause == null) {
            return false;
        }
        final int withTokenIndex = withClause.getStart().getTokenIndex();
        return findAll(parseTree, Cypher25Parser.MatchClauseContext.class).stream()
                .anyMatch(matchClause -> matchClause.getStart().getTokenIndex() > withTokenIndex);
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

    private record ExtractedPattern(List<Node> nodes, List<Edge> edges, boolean optional, Expression localWhereExpression) {
    }
}
