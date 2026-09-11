package com.iisaka.cypher2sql.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a Cypher node label to a SQL table: its qualified name, primary key column(s), per-property column
 * mappings, label inheritance, and uniqueness constraints. Rendering logic for column references and join
 * conditions lives here rather than in a detached SQL-builder class.
 */
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

    /** Creates a minimal mapping: a single label to a table with a single, unmapped-property primary key. */
    public NodeMapping(final String label, final String table, final String primaryKey) {
        this(label, List.of(label), List.of(), null, null, table, List.of(primaryKey), Map.of(), List.of());
    }

    /**
     * Creates a fully-specified mapping.
     *
     * @param label       the node's primary Cypher label
     * @param labels      all Cypher labels this mapping matches; defaults to {@code [label]} if empty
     * @param inherits    labels this node inherits from, also matched by {@link #matchesLabel}
     * @param catalog     the table's catalog qualifier, or {@code null}
     * @param schema      the table's schema qualifier, or {@code null}
     * @param table       the table's unqualified name
     * @param primaryKeys the primary key column(s); defaults to {@code ["id"]} if empty
     * @param properties  Cypher property name to column mapping, for properties whose column differs from the property name
     * @param uniqueKeys  additional unique key column groups
     * @throws IllegalArgumentException if {@code label} or {@code table} is blank
     */
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

    /** This mapping's primary Cypher label. */
    public String label() {
        return label;
    }

    /** All Cypher labels this mapping matches. */
    public List<String> labels() {
        return labels;
    }

    /** Labels this node inherits from, also matched by {@link #matchesLabel}. */
    public List<String> inherits() {
        return inherits;
    }

    /** The table's catalog qualifier, or {@code null}. */
    public String catalog() {
        return catalog;
    }

    /** The table's schema qualifier, or {@code null}. */
    public String schema() {
        return schema;
    }

    /** The table's fully-qualified name, e.g. {@code catalog.schema.table}, omitting absent qualifiers. */
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

    /**
     * The single primary key column.
     *
     * @throws IllegalStateException if the primary key is composite
     */
    public String primaryKey() {
        if (primaryKeys.size() != 1) {
            throw new IllegalStateException("Composite primary key is not scalar for label: " + label);
        }
        return primaryKeys.get(0);
    }

    /** The primary key column(s), in declared order. */
    public List<String> primaryKeys() {
        return primaryKeys;
    }

    /** Cypher property name to column mapping, for properties whose column differs from the property name. */
    public Map<String, PropertyMapping> properties() {
        return properties;
    }

    /** Additional unique key column groups, beyond the primary key. */
    public List<List<String>> uniqueKeys() {
        return uniqueKeys;
    }

    /** Whether this mapping's labels or inherited labels include {@code otherLabel}. */
    public boolean matchesLabel(final String otherLabel) {
        return labels.contains(otherLabel) || inherits.contains(otherLabel);
    }

    /** @throws IllegalArgumentException if {@code property} is blank */
    public String requirePropertyName(final String property) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Property name must be non-blank.");
        }
        return property;
    }

    /** The SQL column for a Cypher property, falling back to the property name itself if unmapped. */
    public String columnForProperty(final String property) {
        final String required = requirePropertyName(property);
        final PropertyMapping mapping = properties.get(required);
        return mapping == null ? required : mapping.column();
    }

    /** Renders {@code alias.*}. */
    public String allColumns(final String alias) {
        return alias + ".*";
    }

    /** Renders {@code alias.*} if {@code propertyOrNull} is {@code null}, else that property's qualified column. */
    public String project(final String alias, final String propertyOrNull) {
        if (propertyOrNull == null) {
            return allColumns(alias);
        }
        return qualifiedColumn(alias, propertyOrNull);
    }

    /** The primary key column, qualified by {@code alias}. */
    public String qualifiedPrimaryKey(final String alias) {
        return qualifiedColumnName(alias, primaryKey());
    }

    /** The primary key column(s), each qualified by {@code alias}. */
    public List<String> qualifiedPrimaryKeys(final String alias) {
        return primaryKeys.stream().map(column -> qualifiedColumnName(alias, column)).toList();
    }

    /** A Cypher property's underlying column, qualified by {@code alias}. */
    public String qualifiedColumn(final String alias, final String property) {
        return qualifiedColumnName(alias, columnForProperty(property));
    }

    /** Renders {@code alias.column}. */
    public String qualifiedColumnName(final String alias, final String column) {
        return alias + "." + requirePropertyName(column);
    }

    /** Join-on clauses (unjoined) equating this table's primary key column(s) to {@code otherColumns}. */
    public List<String> joinOnPrimaryKeys(final String alias, final String otherAlias, final List<String> otherColumns) {
        return joinOnColumns(alias, primaryKeys, otherAlias, otherColumns);
    }

    /**
     * Join-on clauses (unjoined, to be combined with {@code AND}) equating {@code columns} on {@code alias}
     * to {@code otherColumns} on {@code otherAlias}, position by position.
     *
     * @throws IllegalArgumentException if {@code columns} and {@code otherColumns} differ in size
     */
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

    /** A join-on clause equating this table's primary key to an already-rendered expression. */
    public String joinOnPrimaryKey(final String alias, final String otherExpression) {
        return qualifiedPrimaryKey(alias) + " = " + otherExpression;
    }

    /** A join-on clause equating a foreign key on {@code alias} to another table's primary key on {@code otherAlias}. */
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
