package com.iisaka.cypher2sql.schema;

public final class NodeMapping {
    private final String label;
    private final String table;
    private final String primaryKey;

    public NodeMapping(final String label, final String table, final String primaryKey) {
        this.label = label;
        this.table = table;
        this.primaryKey = primaryKey;
    }

    public String label() {
        return label;
    }

    public String table() {
        return table;
    }

    public String primaryKey() {
        return primaryKey;
    }

    public boolean matchesLabel(final String otherLabel) {
        return label.equals(otherLabel);
    }

    public String requirePropertyName(final String property) {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Property name must be non-blank.");
        }
        return property;
    }

    public String allColumns(final String alias) {
        return alias + ".*";
    }

    public String project(final String alias, final String propertyOrNull) {
        if (propertyOrNull == null) {
            return allColumns(alias);
        }
        return qualifiedColumn(alias, requirePropertyName(propertyOrNull));
    }

    public String qualifiedPrimaryKey(final String alias) {
        return alias + "." + primaryKey;
    }

    public String qualifiedColumn(final String alias, final String column) {
        return alias + "." + column;
    }

    public String joinOnPrimaryKey(final String alias, final String otherExpression) {
        return qualifiedPrimaryKey(alias) + " = " + otherExpression;
    }

    public String joinFromForeignKey(
            final String alias,
            final String foreignKey,
            final String otherAlias,
            final String otherPrimaryKey) {
        return qualifiedColumn(alias, requirePropertyName(foreignKey))
                + " = "
                + qualifiedColumn(otherAlias, requirePropertyName(otherPrimaryKey));
    }
}
