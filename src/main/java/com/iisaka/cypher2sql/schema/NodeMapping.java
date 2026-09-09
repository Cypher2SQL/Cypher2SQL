package com.iisaka.cypher2sql.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NodeMapping {
    private final String label;
    private final List<String> labels;
    private final List<String> inherits;
    private final String catalog;
    private final String schema;
    private final String table;
    private final List<String> primaryKeys;
    private final Map<String, PropertyMapping> properties;
    private final List<List<String>> uniqueKeys;

    public NodeMapping(final String label, final String table, final String primaryKey) {
        this(label, List.of(label), List.of(), null, null, table, List.of(primaryKey), Map.of(), List.of());
    }

    public NodeMapping(
            final String label,
            final List<String> labels,
            final List<String> inherits,
            final String catalog,
            final String schema,
            final String table,
            final List<String> primaryKeys,
            final Map<String, PropertyMapping> properties,
            final List<List<String>> uniqueKeys) {
        this.label = requireNonBlank(label, "label");
        this.labels = List.copyOf(labels == null || labels.isEmpty() ? List.of(label) : labels);
        this.inherits = List.copyOf(inherits == null ? List.of() : inherits);
        this.catalog = blankToNull(catalog);
        this.schema = blankToNull(schema);
        this.table = requireNonBlank(table, "table");
        this.primaryKeys = List.copyOf(primaryKeys == null || primaryKeys.isEmpty() ? List.of("id") : primaryKeys);
        this.properties = Map.copyOf(properties == null ? Map.of() : new LinkedHashMap<>(properties));
        this.uniqueKeys = copyNested(uniqueKeys == null ? List.of() : uniqueKeys);
    }

    public String label() {
        return label;
    }

    public List<String> labels() {
        return labels;
    }

    public List<String> inherits() {
        return inherits;
    }

    public String catalog() {
        return catalog;
    }

    public String schema() {
        return schema;
    }

    public String table() {
        final List<String> parts = new ArrayList<>();
        if (catalog != null) {
            parts.add(catalog);
        }
        if (schema != null) {
            parts.add(schema);
        }
        parts.add(table);
        return String.join(".", parts);
    }

    public String primaryKey() {
        if (primaryKeys.size() != 1) {
            throw new IllegalStateException("Composite primary key is not scalar for label: " + label);
        }
        return primaryKeys.get(0);
    }

    public List<String> primaryKeys() {
        return primaryKeys;
    }

    public Map<String, PropertyMapping> properties() {
        return properties;
    }

    public List<List<String>> uniqueKeys() {
        return uniqueKeys;
    }

    public boolean matchesLabel(final String otherLabel) {
        return labels.contains(otherLabel) || inherits.contains(otherLabel);
    }

    public String requirePropertyName(final String property) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Property name must be non-blank.");
        }
        return property;
    }

    public String columnForProperty(final String property) {
        final String required = requirePropertyName(property);
        final PropertyMapping mapping = properties.get(required);
        return mapping == null ? required : mapping.column();
    }

    public String allColumns(final String alias) {
        return alias + ".*";
    }

    public String project(final String alias, final String propertyOrNull) {
        if (propertyOrNull == null) {
            return allColumns(alias);
        }
        return qualifiedColumn(alias, propertyOrNull);
    }

    public String qualifiedPrimaryKey(final String alias) {
        return qualifiedColumnName(alias, primaryKey());
    }

    public List<String> qualifiedPrimaryKeys(final String alias) {
        return primaryKeys.stream().map(column -> qualifiedColumnName(alias, column)).toList();
    }

    public String qualifiedColumn(final String alias, final String property) {
        return qualifiedColumnName(alias, columnForProperty(property));
    }

    public String qualifiedColumnName(final String alias, final String column) {
        return alias + "." + requirePropertyName(column);
    }

    public List<String> joinOnPrimaryKeys(final String alias, final String otherAlias, final List<String> otherColumns) {
        return joinOnColumns(alias, primaryKeys, otherAlias, otherColumns);
    }

    public List<String> joinOnColumns(
            final String alias,
            final List<String> columns,
            final String otherAlias,
            final List<String> otherColumns) {
        if (columns.size() != otherColumns.size()) {
            throw new IllegalArgumentException("Join key arity mismatch: " + columns.size() + " != " + otherColumns.size());
        }
        final List<String> clauses = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            clauses.add(qualifiedColumnName(alias, columns.get(i)) + " = " + qualifiedColumnName(otherAlias, otherColumns.get(i)));
        }
        return clauses;
    }

    public String joinOnPrimaryKey(final String alias, final String otherExpression) {
        return qualifiedPrimaryKey(alias) + " = " + otherExpression;
    }

    public String joinFromForeignKey(
            final String alias,
            final String foreignKey,
            final String otherAlias,
            final String otherPrimaryKey) {
        return qualifiedColumnName(alias, requirePropertyName(foreignKey))
                + " = "
                + qualifiedColumnName(otherAlias, requirePropertyName(otherPrimaryKey));
    }

    private String requireNonBlank(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Node mapping missing required field: " + field);
        }
        return value;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<List<String>> copyNested(final List<List<String>> nested) {
        final List<List<String>> copied = new ArrayList<>();
        for (final List<String> item : nested) {
            copied.add(List.copyOf(item));
        }
        return List.copyOf(copied);
    }
}
