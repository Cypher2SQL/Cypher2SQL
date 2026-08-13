package com.iisaka.cypher2sql.schema;

import java.util.List;
import java.util.Map;

public final class EdgeMapping {
    public enum RelationshipKind {
        JOIN_TABLE,
        SELF_REFERENTIAL,
        ONE_TO_MANY,
        MANY_TO_ONE
    }

    public enum Cardinality {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }

    private final String type;
    private final String fromLabel;
    private final String toLabel;
    private final RelationshipKind relationshipKind;
    private final Cardinality cardinality;
    private final boolean unique;

    private final String joinTable;
    private final List<String> fromJoinKeys;
    private final List<String> toJoinKeys;

    private final List<String> fromKeys;
    private final List<String> toKeys;

    private final List<String> parentPrimaryKeys;
    private final List<String> childForeignKeys;
    private final Map<String, PropertyMapping> properties;

    private EdgeMapping(
            final String type,
            final String fromLabel,
            final String toLabel,
            final RelationshipKind relationshipKind,
            final Cardinality cardinality,
            final boolean unique,
            final String joinTable,
            final List<String> fromJoinKeys,
            final List<String> toJoinKeys,
            final List<String> fromKeys,
            final List<String> toKeys,
            final List<String> parentPrimaryKeys,
            final List<String> childForeignKeys,
            final Map<String, PropertyMapping> properties) {
        this.type = type;
        this.fromLabel = fromLabel;
        this.toLabel = toLabel;
        this.relationshipKind = relationshipKind;
        this.cardinality = cardinality;
        this.unique = unique;
        this.joinTable = joinTable;
        this.fromJoinKeys = List.copyOf(fromJoinKeys == null ? List.of() : fromJoinKeys);
        this.toJoinKeys = List.copyOf(toJoinKeys == null ? List.of() : toJoinKeys);
        this.fromKeys = List.copyOf(fromKeys == null ? List.of() : fromKeys);
        this.toKeys = List.copyOf(toKeys == null ? List.of() : toKeys);
        this.parentPrimaryKeys = List.copyOf(parentPrimaryKeys == null ? List.of() : parentPrimaryKeys);
        this.childForeignKeys = List.copyOf(childForeignKeys == null ? List.of() : childForeignKeys);
        this.properties = Map.copyOf(properties == null ? Map.of() : properties);
    }

    public static EdgeMapping forJoinTable(
            final String type,
            final String fromLabel,
            final String toLabel,
            final String joinTable,
            final String fromJoinKey,
            final String toJoinKey) {
        return forJoinTable(type, fromLabel, toLabel, joinTable, List.of(fromJoinKey), List.of(toJoinKey), Map.of());
    }

    public static EdgeMapping forJoinTable(
            final String type,
            final String fromLabel,
            final String toLabel,
            final String joinTable,
            final List<String> fromJoinKeys,
            final List<String> toJoinKeys,
            final Map<String, PropertyMapping> properties) {
        return new EdgeMapping(type, fromLabel, toLabel, RelationshipKind.JOIN_TABLE, Cardinality.MANY_TO_MANY, false,
                joinTable, fromJoinKeys, toJoinKeys, null, null, null, null, properties);
    }

    public static EdgeMapping forSelfReferential(
            final String type,
            final String label,
            final String fromKey,
            final String toKey) {
        return forSelfReferential(type, label, List.of(fromKey), List.of(toKey), Map.of());
    }

    public static EdgeMapping forSelfReferential(
            final String type,
            final String label,
            final List<String> fromKeys,
            final List<String> toKeys,
            final Map<String, PropertyMapping> properties) {
        return new EdgeMapping(type, label, label, RelationshipKind.SELF_REFERENTIAL, Cardinality.MANY_TO_ONE, false,
                null, null, null, fromKeys, toKeys, null, null, properties);
    }

    public static EdgeMapping forOneToMany(
            final String type,
            final String parentLabel,
            final String childLabel,
            final String parentPrimaryKey,
            final String childForeignKey) {
        return forOneToMany(type, parentLabel, childLabel, List.of(parentPrimaryKey), List.of(childForeignKey), Map.of());
    }

    public static EdgeMapping forOneToMany(
            final String type,
            final String parentLabel,
            final String childLabel,
            final List<String> parentPrimaryKeys,
            final List<String> childForeignKeys,
            final Map<String, PropertyMapping> properties) {
        return new EdgeMapping(type, parentLabel, childLabel, RelationshipKind.ONE_TO_MANY, Cardinality.ONE_TO_MANY, false,
                null, null, null, null, null, parentPrimaryKeys, childForeignKeys, properties);
    }

    public static EdgeMapping forManyToOne(
            final String type,
            final String fromLabel,
            final String toLabel,
            final List<String> fromForeignKeys,
            final List<String> toPrimaryKeys,
            final Map<String, PropertyMapping> properties) {
        return new EdgeMapping(type, fromLabel, toLabel, RelationshipKind.MANY_TO_ONE, Cardinality.MANY_TO_ONE, false,
                null, null, null, null, null, toPrimaryKeys, fromForeignKeys, properties);
    }

    public EdgeMapping withMetadata(final Cardinality cardinality, final boolean unique) {
        return new EdgeMapping(type, fromLabel, toLabel, relationshipKind,
                cardinality == null ? this.cardinality : cardinality,
                unique,
                joinTable, fromJoinKeys, toJoinKeys, fromKeys, toKeys, parentPrimaryKeys, childForeignKeys, properties);
    }

    public String type() {
        return type;
    }

    public String fromLabel() {
        return fromLabel;
    }

    public String toLabel() {
        return toLabel;
    }

    public RelationshipKind relationshipKind() {
        return relationshipKind;
    }

    public Cardinality cardinality() {
        return cardinality;
    }

    public boolean unique() {
        return unique;
    }

    public String joinTable() {
        return joinTable;
    }

    public String fromJoinKey() {
        return scalar(fromJoinKeys, "fromJoinKey");
    }

    public List<String> fromJoinKeys() {
        return fromJoinKeys;
    }

    public String toJoinKey() {
        return scalar(toJoinKeys, "toJoinKey");
    }

    public List<String> toJoinKeys() {
        return toJoinKeys;
    }

    public String fromKey() {
        return scalar(fromKeys, "fromKey");
    }

    public List<String> fromKeys() {
        return fromKeys;
    }

    public String toKey() {
        return scalar(toKeys, "toKey");
    }

    public List<String> toKeys() {
        return toKeys;
    }

    public String parentPrimaryKey() {
        return scalar(parentPrimaryKeys, "parentPrimaryKey");
    }

    public List<String> parentPrimaryKeys() {
        return parentPrimaryKeys;
    }

    public String childForeignKey() {
        return scalar(childForeignKeys, "childForeignKey");
    }

    public List<String> childForeignKeys() {
        return childForeignKeys;
    }

    public Map<String, PropertyMapping> properties() {
        return properties;
    }

    public String columnForProperty(final String property) {
        final PropertyMapping mapping = properties.get(property);
        if (mapping == null) {
            throw new IllegalArgumentException("No relationship property mapping for property: " + property);
        }
        return mapping.column();
    }

    public boolean matchesDirectedLabels(final String from, final String to) {
        return fromLabel.equals(from) && toLabel.equals(to);
    }

    public boolean matchesUndirectedLabels(final String left, final String right) {
        return matchesDirectedLabels(left, right) || matchesDirectedLabels(right, left);
    }

    public boolean isLeftParent(final String leftLabel, final String rightLabel) {
        return matchesDirectedLabels(leftLabel, rightLabel);
    }

    public boolean isRightParent(final String leftLabel, final String rightLabel) {
        return matchesDirectedLabels(rightLabel, leftLabel);
    }

    public List<String> joinTableProjection(final String joinAlias) {
        return List.of(joinAlias + ".*");
    }

    public List<String> selfReferentialProjection(final String leftAlias, final String rightAlias) {
        return pairProjection(leftAlias, fromKeys, rightAlias, toKeys);
    }

    public List<String> oneToManyProjectionForLeftParent(final String leftAlias, final String rightAlias) {
        return pairProjection(rightAlias, childForeignKeys, leftAlias, parentPrimaryKeys);
    }

    public List<String> oneToManyProjectionForRightParent(final String leftAlias, final String rightAlias) {
        return pairProjection(leftAlias, childForeignKeys, rightAlias, parentPrimaryKeys);
    }

    private List<String> pairProjection(
            final String leftAlias,
            final List<String> leftColumns,
            final String rightAlias,
            final List<String> rightColumns) {
        return java.util.stream.Stream.concat(
                leftColumns.stream().map(column -> leftAlias + "." + column),
                rightColumns.stream().map(column -> rightAlias + "." + column)).toList();
    }

    private static String scalar(final List<String> values, final String name) {
        if (values.size() != 1) {
            throw new IllegalStateException("Composite " + name + " is not scalar.");
        }
        return values.get(0);
    }
}
