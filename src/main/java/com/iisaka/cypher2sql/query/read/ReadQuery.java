package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.expression.BinaryExpression;
import com.iisaka.cypher2sql.query.cypher.expression.CaseExpression;
import com.iisaka.cypher2sql.query.cypher.expression.ConstantExpression;
import com.iisaka.cypher2sql.query.cypher.expression.Expression;
import com.iisaka.cypher2sql.query.cypher.expression.FunctionExpression;
import com.iisaka.cypher2sql.query.cypher.expression.KnownFunction;
import com.iisaka.cypher2sql.query.cypher.OrderItem;
import com.iisaka.cypher2sql.query.cypher.ProjectionItem;
import com.iisaka.cypher2sql.query.cypher.expression.PropertyExpression;
import com.iisaka.cypher2sql.query.cypher.expression.UnaryExpression;
import com.iisaka.cypher2sql.query.cypher.expression.VariableExpression;
import com.iisaka.cypher2sql.query.cypher.expression.WildcardExpression;
import com.iisaka.cypher2sql.query.sql.JoinClause;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReadQuery {
    public record FinalStage(
            Expression whereExpression,
            List<ProjectionItem> projectionItems,
            boolean distinct,
            List<OrderItem> orderItems,
            Long skip,
            Long limit) {
    }

    private final List<BoundPattern> patterns;
    private final Expression whereExpression;
    private final List<ProjectionItem> projectionItems;
    private final boolean distinct;
    private final List<OrderItem> orderItems;
    private final Long skip;
    private final Long limit;
    private final FinalStage finalStage;

    public ReadQuery(
            final List<BoundPattern> patterns,
            final Expression whereExpression,
            final List<ProjectionItem> projectionItems,
            final boolean distinct,
            final List<OrderItem> orderItems,
            final Long skip,
            final Long limit) {
        this(patterns, whereExpression, projectionItems, distinct, orderItems, skip, limit, null);
    }

    public ReadQuery(
            final List<BoundPattern> patterns,
            final Expression whereExpression,
            final List<ProjectionItem> projectionItems,
            final boolean distinct,
            final List<OrderItem> orderItems,
            final Long skip,
            final Long limit,
            final FinalStage finalStage) {
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.whereExpression = whereExpression;
        this.projectionItems = Collections.unmodifiableList(new ArrayList<>(projectionItems));
        this.distinct = distinct;
        this.orderItems = Collections.unmodifiableList(new ArrayList<>(orderItems));
        this.skip = skip;
        this.limit = limit;
        this.finalStage = finalStage;
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

    public SelectQuery asSql() {
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("No patterns parsed from Cypher query.");
        }
        if (patterns.get(0).optional()) {
            throw new UnsupportedOperationException("OPTIONAL MATCH cannot be the first clause yet.");
        }

        final Map<String, String> aliasesByVariable = new HashMap<>();
        final Map<String, BoundNode> nodesByAlias = new HashMap<>();
        registerNodes(patterns.get(0), aliasesByVariable, nodesByAlias);
        for (int i = 1; i < patterns.size(); i++) {
            final BoundPattern pattern = patterns.get(i);
            if (!pattern.optional()) {
                throw new UnsupportedOperationException(
                        "Multiple top-level MATCH patterns are not supported yet. Found: " + patterns.size());
            }
            final String rootVariable = pattern.root().variable();
            if (rootVariable == null || rootVariable.isBlank() || !aliasesByVariable.containsKey(rootVariable)) {
                throw new UnsupportedOperationException(
                        "OPTIONAL MATCH must reference a variable already bound by a preceding MATCH clause.");
            }
            registerNodes(pattern, aliasesByVariable, nodesByAlias);
        }

        final BoundPattern basePattern = patterns.get(0);
        final SelectQuery select = SelectQuery.from(basePattern.root().mapping().table(), basePattern.root().alias());
        if (distinct) {
            select.setDistinct();
        }
        final int[] nextJoinAliasCounter = {nodesByAlias.size()};
        final Map<String, List<String>> edgeProjections = new HashMap<>();
        final Map<String, Map<String, String>> edgePropertyProjections = new HashMap<>();

        applyTraversals(basePattern, select, JoinClause.JoinType.INNER, nextJoinAliasCounter, edgeProjections, edgePropertyProjections);
        for (int i = 1; i < patterns.size(); i++) {
            final BoundPattern pattern = patterns.get(i);
            applyTraversals(pattern, select, JoinClause.JoinType.LEFT, nextJoinAliasCounter, edgeProjections, edgePropertyProjections);
            if (pattern.localWhereExpression() != null) {
                if (pattern.traversals().isEmpty()) {
                    throw new UnsupportedOperationException(
                            "WHERE on an OPTIONAL MATCH with no relationships is not supported.");
                }
                final String rendered = renderExpression(
                        pattern.localWhereExpression(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false);
                select.andLastJoinCondition(rendered);
            }
        }

        applyWhere(select, whereExpression, aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
        applyReturnProjection(select, projectionItems, basePattern.root(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
        applyOrderBy(select, orderItems, aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
        if (limit != null) {
            select.setLimit(limit);
        }
        if (skip != null) {
            select.setOffset(skip);
        }
        if (finalStage == null) {
            return select;
        }
        return renderFinalStage(select, aliasesByVariable, nodesByAlias, edgeProjections);
    }

    private SelectQuery renderFinalStage(
            final SelectQuery innerSelect,
            final Map<String, String> innerAliasesByVariable,
            final Map<String, BoundNode> innerNodesByAlias,
            final Map<String, List<String>> innerEdgeProjections) {
        final SelectQuery outer = SelectQuery.fromSubquery(innerSelect, "with0");
        final Map<String, String> aliasesByVariable = new HashMap<>();
        final Map<String, BoundNode> nodesByAlias = new HashMap<>();
        final Map<String, List<String>> edgeProjections = new HashMap<>();
        final Map<String, Map<String, String>> edgePropertyProjections = new HashMap<>();
        boolean sawNodePassthrough = false;

        for (final ProjectionItem item : projectionItems) {
            if (item.expression() instanceof VariableExpression variable
                    && innerAliasesByVariable.containsKey(variable.name())) {
                if (sawNodePassthrough) {
                    throw new UnsupportedOperationException(
                            "WITH can pass through at most one node variable unchanged; alias the rest to a scalar expression.");
                }
                sawNodePassthrough = true;
                final BoundNode original = innerNodesByAlias.get(innerAliasesByVariable.get(variable.name()));
                final BoundNode carried = new BoundNode(original.node(), original.mapping(), "with0");
                aliasesByVariable.put(item.alias() != null ? item.alias() : variable.name(), "with0");
                nodesByAlias.put("with0", carried);
            } else if (item.expression() instanceof VariableExpression variable
                    && innerEdgeProjections.containsKey(variable.name())) {
                throw new UnsupportedOperationException(
                        "Relationship variables cannot be passed through WITH yet: " + variable.name());
            } else if (item.alias() == null) {
                throw new UnsupportedOperationException(
                        "WITH items must be aliased unless they pass through a plain variable.");
            } else {
                edgeProjections.put(item.alias(), List.of("with0." + item.alias()));
            }
        }

        if (finalStage.distinct()) {
            outer.setDistinct();
        }
        applyWhere(outer, finalStage.whereExpression(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
        applyReturnProjection(
                outer, finalStage.projectionItems(), nodesByAlias.get("with0"),
                aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
        applyOrderBy(outer, finalStage.orderItems(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
        if (finalStage.limit() != null) {
            outer.setLimit(finalStage.limit());
        }
        if (finalStage.skip() != null) {
            outer.setOffset(finalStage.skip());
        }
        return outer;
    }

    private void registerNodes(
            final BoundPattern pattern,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias) {
        for (final BoundNode node : pattern.nodes()) {
            nodesByAlias.putIfAbsent(node.alias(), node);
            if (node.variable() != null && !node.variable().isBlank()) {
                aliasesByVariable.putIfAbsent(node.variable(), node.alias());
            }
        }
    }

    private void applyTraversals(
            final BoundPattern pattern,
            final SelectQuery select,
            final JoinClause.JoinType joinType,
            final int[] nextJoinAliasCounter,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        for (final BoundTraversal traversal : pattern.traversals()) {
            final List<String> projection = traversal.applyTo(select, nextJoinAliasCounter, joinType);
            if (traversal.edge().variable() != null && !traversal.edge().variable().isBlank()) {
                edgeProjections.put(traversal.edge().variable(), projection);
                if (traversal.mapping().relationshipKind()
                        == com.iisaka.cypher2sql.schema.EdgeMapping.RelationshipKind.JOIN_TABLE
                        && projection.size() == 1
                        && projection.get(0).endsWith(".*")) {
                    final String joinAlias = projection.get(0).substring(0, projection.get(0).length() - 2);
                    final Map<String, String> properties = new HashMap<>();
                    traversal.mapping().properties().forEach((property, mapping) ->
                            properties.put(property, joinAlias + "." + mapping.column()));
                    edgePropertyProjections.put(traversal.edge().variable(), properties);
                }
            }
        }
    }

    private void applyWhere(
            final SelectQuery select,
            final Expression whereExpression,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        if (whereExpression == null) {
            return;
        }
        select.addWhere(renderExpression(whereExpression, aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false));
    }

    private void applyReturnProjection(
            final SelectQuery select,
            final List<ProjectionItem> projectionItems,
            final BoundNode defaultRoot,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        if (projectionItems.isEmpty()) {
            select.addSelectColumn(defaultRoot.mapping().allColumns(defaultRoot.alias()));
            return;
        }
        for (final ProjectionItem item : projectionItems) {
            if (item.expression() instanceof VariableExpression variable) {
                final List<String> edgeProjection = edgeProjections.get(variable.name());
                if (edgeProjection != null && item.alias() == null) {
                    for (final String column : edgeProjection) {
                        select.addSelectColumn(column);
                    }
                    continue;
                }
            }
            final String rendered = renderExpression(
                    item.expression(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, true);
            select.addSelectColumn(item.alias() == null ? rendered : rendered + " AS " + item.alias());
        }
    }

    private void applyOrderBy(
            final SelectQuery select,
            final List<OrderItem> orderItems,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        for (final OrderItem orderItem : orderItems) {
            final String rendered = renderExpression(
                    orderItem.expression(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false);
            select.addOrderBy(orderItem.descending() ? rendered + " DESC" : rendered + " ASC");
        }
    }

    private String renderExpression(
            final Expression expression,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections,
            final boolean topLevelProjection) {
        return switch (expression) {
            case VariableExpression variable ->
                    renderVariable(variable.name(), aliasesByVariable, nodesByAlias, edgeProjections, topLevelProjection);
            case PropertyExpression property ->
                    renderProperty(property, aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
            case ConstantExpression constant -> renderConstant(constant.value());
            case FunctionExpression function ->
                    renderFunction(function, aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
            case BinaryExpression binary -> "("
                    + renderExpression(binary.left(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false)
                    + " "
                    + binary.operator().sql()
                    + " "
                    + renderExpression(binary.right(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false)
                    + ")";
            case UnaryExpression unary -> "("
                    + unary.operator().sql()
                    + " "
                    + renderExpression(unary.operand(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false)
                    + ")";
            case CaseExpression expressionCase ->
                    renderCase(expressionCase, aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections);
            case WildcardExpression ignored -> "*";
        };
    }

    private String renderProperty(
            final PropertyExpression property,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        if (property.receiver() instanceof VariableExpression variable) {
            final String alias = aliasesByVariable.get(variable.name());
            if (alias != null) {
                return nodesByAlias.get(alias).mapping().qualifiedColumn(alias, property.property());
            }
            final Map<String, String> properties = edgePropertyProjections.get(variable.name());
            if (properties != null && properties.containsKey(property.property())) {
                return properties.get(property.property());
            }
            throw new IllegalArgumentException(
                    "RETURN edge properties are not supported yet: " + variable.name() + "." + property.property());
        }
        return renderExpression(property.receiver(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false)
                + "." + property.property();
    }

    private String renderVariable(
            final String variable,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final boolean topLevelProjection) {
        final String alias = aliasesByVariable.get(variable);
        if (alias != null) {
            final BoundNode node = nodesByAlias.get(alias);
            if (topLevelProjection) {
                return node.mapping().allColumns(alias);
            }
            return node.mapping().qualifiedPrimaryKey(alias);
        }
        final List<String> edgeProjection = edgeProjections.get(variable);
        if (edgeProjection != null) {
            if (topLevelProjection) {
                if (edgeProjection.size() == 1) {
                    return edgeProjection.get(0);
                }
                throw new IllegalArgumentException("RETURN edge variable expands to multiple columns: " + variable);
            }
            if (edgeProjection.size() == 1 && !edgeProjection.get(0).endsWith(".*")) {
                return edgeProjection.get(0);
            }
            throw new IllegalArgumentException("Expression references edge variable that is not scalar: " + variable);
        }
        throw new IllegalArgumentException("RETURN references unknown variable: " + variable);
    }

    private String renderFunction(
            final FunctionExpression function,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        final String sqlName = KnownFunction.forName(function.name())
                .map(KnownFunction::sqlName)
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Function is parsed but not rendered yet: " + function.name()));
        final List<String> arguments = new ArrayList<>();
        for (final Expression argument : function.arguments()) {
            arguments.add(renderExpression(argument, aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false));
        }
        return sqlName + "(" + String.join(", ", arguments) + ")";
    }

    private String renderCase(
            final CaseExpression expressionCase,
            final Map<String, String> aliasesByVariable,
            final Map<String, BoundNode> nodesByAlias,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        final StringBuilder sql = new StringBuilder("CASE");
        if (expressionCase.subject() != null) {
            sql.append(" ").append(renderExpression(
                    expressionCase.subject(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false));
        }
        for (final CaseExpression.WhenThen whenThen : expressionCase.whenThens()) {
            sql.append(" WHEN ")
                    .append(renderExpression(
                            whenThen.whenExpression(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false))
                    .append(" THEN ")
                    .append(renderExpression(
                            whenThen.thenExpression(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false));
        }
        if (expressionCase.elseExpression() != null) {
            sql.append(" ELSE ").append(renderExpression(
                    expressionCase.elseExpression(), aliasesByVariable, nodesByAlias, edgeProjections, edgePropertyProjections, false));
        }
        sql.append(" END");
        return sql.toString();
    }

    private String renderConstant(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String stringValue) {
            return "'" + stringValue.replace("'", "''") + "'";
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "TRUE" : "FALSE";
        }
        if (value instanceof Double doubleValue) {
            if (doubleValue == Math.rint(doubleValue)) {
                return Long.toString((long) doubleValue.doubleValue());
            }
            return doubleValue.toString();
        }
        if (value instanceof Float floatValue) {
            if (floatValue == (float) Math.rint(floatValue)) {
                return Integer.toString((int) floatValue.floatValue());
            }
            return floatValue.toString();
        }
        return value.toString();
    }
}
