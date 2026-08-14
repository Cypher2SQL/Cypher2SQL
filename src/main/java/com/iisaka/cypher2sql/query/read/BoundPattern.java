package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.Expression;
import com.iisaka.cypher2sql.query.cypher.ProjectionItem;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BoundPattern {
    private static final java.util.Map<String, String> SUPPORTED_FUNCTIONS = java.util.Map.ofEntries(
            java.util.Map.entry("count", "COUNT"),
            java.util.Map.entry("sum", "SUM"),
            java.util.Map.entry("avg", "AVG"),
            java.util.Map.entry("min", "MIN"),
            java.util.Map.entry("max", "MAX"),
            java.util.Map.entry("coalesce", "COALESCE"),
            java.util.Map.entry("abs", "ABS"),
            java.util.Map.entry("ceil", "CEIL"),
            java.util.Map.entry("floor", "FLOOR"),
            java.util.Map.entry("round", "ROUND"),
            java.util.Map.entry("sqrt", "SQRT"),
            java.util.Map.entry("log", "LOG"),
            java.util.Map.entry("log10", "LOG10"),
            java.util.Map.entry("exp", "EXP"),
            java.util.Map.entry("sin", "SIN"),
            java.util.Map.entry("cos", "COS"),
            java.util.Map.entry("tan", "TAN"),
            java.util.Map.entry("trim", "TRIM"),
            java.util.Map.entry("ltrim", "LTRIM"),
            java.util.Map.entry("rtrim", "RTRIM"),
            java.util.Map.entry("substring", "SUBSTRING"),
            java.util.Map.entry("replace", "REPLACE"),
            java.util.Map.entry("left", "LEFT"),
            java.util.Map.entry("right", "RIGHT"),
            java.util.Map.entry("toupper", "UPPER"),
            java.util.Map.entry("tolower", "LOWER"));

    private final List<BoundNode> nodes;
    private final List<BoundTraversal> traversals;
    private final Map<String, String> aliasesByVariable;

    public BoundPattern(final List<BoundNode> nodes, final List<BoundTraversal> traversals) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.traversals = Collections.unmodifiableList(new ArrayList<>(traversals));
        if (this.nodes.size() != this.traversals.size() + 1) {
            throw new IllegalArgumentException("BoundPattern requires exactly one more node than traversal.");
        }
        this.aliasesByVariable = aliasesByVariable(this.nodes);
    }

    public List<BoundNode> nodes() {
        return nodes;
    }

    public List<BoundTraversal> traversals() {
        return traversals;
    }

    public BoundNode root() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("BoundPattern has no nodes.");
        }
        return nodes.get(0);
    }

    public String aliasForVariable(final String variable) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        return aliasesByVariable.get(variable);
    }

    public SelectQuery asSql(final Expression whereExpression, final List<ProjectionItem> projectionItems) {
        final SelectQuery select = SelectQuery.from(root().mapping().table(), root().alias());
        final int[] nextJoinAliasCounter = new int[] {nodes.size()};
        final Map<String, List<String>> edgeProjections = new HashMap<>();
        final Map<String, Map<String, String>> edgePropertyProjections = new HashMap<>();

        for (final BoundTraversal traversal : traversals) {
            final List<String> projection = traversal.applyTo(select, nextJoinAliasCounter);
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
        applyWhere(select, whereExpression, edgeProjections, edgePropertyProjections);
        applyReturnProjection(select, projectionItems, edgeProjections, edgePropertyProjections);
        return select;
    }

    private void applyWhere(
            final SelectQuery select,
            final Expression whereExpression,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        if (whereExpression == null) {
            return;
        }
        select.addWhere(renderExpression(whereExpression, edgeProjections, edgePropertyProjections, false));
    }

    private void applyReturnProjection(
            final SelectQuery select,
            final List<ProjectionItem> projectionItems,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        if (projectionItems.isEmpty()) {
            select.addSelectColumn(root().mapping().allColumns(root().alias()));
            return;
        }
        for (final ProjectionItem item : projectionItems) {
            if (item.expression() instanceof Expression.VariableExpression variable) {
                final List<String> edgeProjection = edgeProjections.get(variable.name());
                if (edgeProjection != null && item.alias() == null) {
                    for (final String column : edgeProjection) {
                        select.addSelectColumn(column);
                    }
                    continue;
                }
            }
            final String rendered = renderExpression(item.expression(), edgeProjections, edgePropertyProjections, true);
            select.addSelectColumn(item.alias() == null ? rendered : rendered + " AS " + item.alias());
        }
    }

    private String renderExpression(
            final Expression expression,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections,
            final boolean topLevelProjection) {
        return switch (expression) {
            case Expression.VariableExpression variable -> renderVariable(variable.name(), edgeProjections, topLevelProjection);
            case Expression.PropertyExpression property -> renderProperty(property, edgeProjections, edgePropertyProjections);
            case Expression.ConstantExpression constant -> renderConstant(constant.value());
            case Expression.FunctionExpression function -> renderFunction(function, edgeProjections, edgePropertyProjections);
            case Expression.BinaryExpression binary -> "("
                    + renderExpression(binary.left(), edgeProjections, edgePropertyProjections, false)
                    + " "
                    + binary.operator().sql()
                    + " "
                    + renderExpression(binary.right(), edgeProjections, edgePropertyProjections, false)
                    + ")";
            case Expression.UnaryExpression unary -> "("
                    + unary.operator().sql()
                    + " "
                    + renderExpression(unary.operand(), edgeProjections, edgePropertyProjections, false)
                    + ")";
            case Expression.CaseExpression expressionCase -> renderCase(expressionCase, edgeProjections, edgePropertyProjections);
            case Expression.WildcardExpression ignored -> "*";
        };
    }

    private String renderProperty(
            final Expression.PropertyExpression property,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        if (property.receiver() instanceof Expression.VariableExpression variable) {
            final String alias = aliasForVariable(variable.name());
            if (alias != null) {
                return nodeForAlias(alias).mapping().qualifiedColumn(alias, property.property());
            }
            final Map<String, String> properties = edgePropertyProjections.get(variable.name());
            if (properties != null && properties.containsKey(property.property())) {
                return properties.get(property.property());
            }
            throw new IllegalArgumentException(
                    "RETURN edge properties are not supported yet: " + variable.name() + "." + property.property());
        }
        return renderExpression(property.receiver(), edgeProjections, edgePropertyProjections, false) + "." + property.property();
    }

    private String renderVariable(
            final String variable,
            final Map<String, List<String>> edgeProjections,
            final boolean topLevelProjection) {
        final String alias = aliasForVariable(variable);
        if (alias != null) {
            final BoundNode node = nodeForAlias(alias);
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
            final Expression.FunctionExpression function,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        final String sqlName = SUPPORTED_FUNCTIONS.get(function.name().toLowerCase());
        if (sqlName == null) {
            throw new UnsupportedOperationException(
                    "Function is parsed but not rendered yet: " + function.name());
        }
        final List<String> arguments = new ArrayList<>();
        for (final Expression argument : function.arguments()) {
            arguments.add(renderExpression(argument, edgeProjections, edgePropertyProjections, false));
        }
        return sqlName + "(" + String.join(", ", arguments) + ")";
    }

    private String renderCase(
            final Expression.CaseExpression expressionCase,
            final Map<String, List<String>> edgeProjections,
            final Map<String, Map<String, String>> edgePropertyProjections) {
        final StringBuilder sql = new StringBuilder("CASE");
        if (expressionCase.subject() != null) {
            sql.append(" ").append(renderExpression(expressionCase.subject(), edgeProjections, edgePropertyProjections, false));
        }
        for (final Expression.CaseExpression.WhenThen whenThen : expressionCase.whenThens()) {
            sql.append(" WHEN ")
                    .append(renderExpression(whenThen.whenExpression(), edgeProjections, edgePropertyProjections, false))
                    .append(" THEN ")
                    .append(renderExpression(whenThen.thenExpression(), edgeProjections, edgePropertyProjections, false));
        }
        if (expressionCase.elseExpression() != null) {
            sql.append(" ELSE ").append(renderExpression(expressionCase.elseExpression(), edgeProjections, edgePropertyProjections, false));
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

    private BoundNode nodeForAlias(final String alias) {
        for (final BoundNode node : nodes) {
            if (node.alias().equals(alias)) {
                return node;
            }
        }
        throw new IllegalArgumentException("No node bound for alias: " + alias);
    }

    private Map<String, String> aliasesByVariable(final List<BoundNode> nodes) {
        final Map<String, String> aliases = new HashMap<>();
        for (final BoundNode node : nodes) {
            if (node.variable() != null && !node.variable().isBlank()) {
                aliases.put(node.variable(), node.alias());
            }
        }
        return Collections.unmodifiableMap(aliases);
    }
}
