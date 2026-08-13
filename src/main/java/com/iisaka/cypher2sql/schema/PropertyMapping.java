package com.iisaka.cypher2sql.schema;

public record PropertyMapping(
        String property,
        String column,
        String type,
        boolean nullable,
        boolean quote) {
    public PropertyMapping {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Property name must be non-blank.");
        }
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Column name must be non-blank.");
        }
    }

    public static PropertyMapping of(final String property, final String column) {
        return new PropertyMapping(property, column, null, true, false);
    }
}
