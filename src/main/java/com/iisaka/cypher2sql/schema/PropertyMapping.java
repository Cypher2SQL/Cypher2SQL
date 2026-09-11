package com.iisaka.cypher2sql.schema;

/**
 * Maps one Cypher property name to its underlying SQL column, for a {@link NodeMapping} or
 * {@link EdgeMapping} whose Cypher property name differs from (or needs more metadata than) its column.
 *
 * @param property the Cypher property name
 * @param column   the underlying SQL column name
 * @param type     the column's declared type, or {@code null} if unspecified
 * @param nullable whether the column allows {@code NULL}
 * @param quote    whether the column identifier requires quoting
 */
public record PropertyMapping(
        String property,
        String column,
        String type,
        boolean nullable,
        boolean quote) {
    /** @throws IllegalArgumentException if {@code property} or {@code column} is blank */
    public PropertyMapping {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Property name must be non-blank.");
        }
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Column name must be non-blank.");
        }
    }

    /** Creates a mapping with only a property/column pair; type unspecified, nullable, unquoted. */
    public static PropertyMapping of(final String property, final String column) {
        return new PropertyMapping(property, column, null, true, false);
    }
}
