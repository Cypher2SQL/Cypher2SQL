package com.iisaka.cypher2sql.schema;

import java.util.List;
import java.util.Map;

/**
 * Maps a Cypher relationship type to its underlying SQL join strategy. Four relational shapes are
 * supported, one {@code forXxx} factory each: a many-to-many join table, a self-referential foreign key
 * on the same table, and one-to-many/many-to-one foreign keys between two tables. {@link Cardinality} is
 * separate schema metadata layered on top via {@link #withMetadata}, so a simple SQL join can still be
 * described accurately even when the true cardinality differs from what the join shape alone implies.
 */
public final class EdgeMapping {
    /** The relational join strategy used to translate a relationship of this type to SQL. */
    public enum RelationshipKind {
        /** A many-to-many relationship via a separate join table. */
        JOIN_TABLE,
        /** A relationship between two rows of the same table, via a foreign key column. */
        SELF_REFERENTIAL,
        /** A relationship from a parent table to a child table via the child's foreign key. */
        ONE_TO_MANY,
        /** A relationship from a child table to a parent table via the child's foreign key. */
        MANY_TO_ONE
    }

    /** The true cardinality of a relationship, as schema metadata independent of its {@link RelationshipKind}. */
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

    /** Creates a many-to-many join-table mapping with a single scalar join key on each side. */
    public static EdgeMapping forJoinTable(
            final String type,
            final String fromLabel,
            final String toLabel,
            final String joinTable,
            final String fromJoinKey,
            final String toJoinKey) {
        return forJoinTable(type, fromLabel, toLabel, joinTable, List.of(fromJoinKey), List.of(toJoinKey), Map.of());
    }

    /** Creates a many-to-many join-table mapping, with composite join keys and relationship properties. */
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

    /** Creates a self-referential mapping with a single scalar key on each side. */
    public static EdgeMapping forSelfReferential(
            final String type,
            final String label,
            final String fromKey,
            final String toKey) {
        return forSelfReferential(type, label, List.of(fromKey), List.of(toKey), Map.of());
    }

    /** Creates a self-referential mapping, with composite keys and relationship properties. */
    public static EdgeMapping forSelfReferential(
            final String type,
            final String label,
            final List<String> fromKeys,
            final List<String> toKeys,
            final Map<String, PropertyMapping> properties) {
        return new EdgeMapping(type, label, label, RelationshipKind.SELF_REFERENTIAL, Cardinality.MANY_TO_ONE, false,
                null, null, null, fromKeys, toKeys, null, null, properties);
    }

    /** Creates a one-to-many mapping with a single scalar key on each side. */
    public static EdgeMapping forOneToMany(
            final String type,
            final String parentLabel,
            final String childLabel,
            final String parentPrimaryKey,
            final String childForeignKey) {
        return forOneToMany(type, parentLabel, childLabel, List.of(parentPrimaryKey), List.of(childForeignKey), Map.of());
    }

    /** Creates a one-to-many mapping, with composite keys and relationship properties. */
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

    /** Creates a many-to-one mapping, with composite keys and relationship properties. */
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

    /** Returns a copy of this mapping with its {@link Cardinality} and {@link #unique()} flag overridden. */
    public EdgeMapping withMetadata(final Cardinality cardinality, final boolean unique) {
        return new EdgeMapping(type, fromLabel, toLabel, relationshipKind,
                cardinality == null ? this.cardinality : cardinality,
                unique,
                joinTable, fromJoinKeys, toJoinKeys, fromKeys, toKeys, parentPrimaryKeys, childForeignKeys, properties);
    }

    /** The Cypher relationship type this mapping applies to. */
    public String type() {
        return type;
    }

    /** The label of the node this relationship is directed from. */
    public String fromLabel() {
        return fromLabel;
    }

    /** The label of the node this relationship is directed to. */
    public String toLabel() {
        return toLabel;
    }

    /** The relational join strategy used to translate this relationship to SQL. */
    public RelationshipKind relationshipKind() {
        return relationshipKind;
    }

    /** This relationship's declared cardinality, independent of its {@link #relationshipKind()}. */
    public Cardinality cardinality() {
        return cardinality;
    }

    /** Whether this relationship is constrained to be unique per source node. */
    public boolean unique() {
        return unique;
    }

    /** The join table's name, for a {@link RelationshipKind#JOIN_TABLE} mapping. */
    public String joinTable() {
        return joinTable;
    }

    /** @throws IllegalStateException if the from-side join key is composite */
    public String fromJoinKey() {
        return scalar(fromJoinKeys, "fromJoinKey");
    }

    /** The join table's column(s) referencing the from-side node, for a {@link RelationshipKind#JOIN_TABLE} mapping. */
    public List<String> fromJoinKeys() {
        return fromJoinKeys;
    }

    /** @throws IllegalStateException if the to-side join key is composite */
    public String toJoinKey() {
        return scalar(toJoinKeys, "toJoinKey");
    }

    /** The join table's column(s) referencing the to-side node, for a {@link RelationshipKind#JOIN_TABLE} mapping. */
    public List<String> toJoinKeys() {
        return toJoinKeys;
    }

    /** @throws IllegalStateException if the from-side key is composite */
    public String fromKey() {
        return scalar(fromKeys, "fromKey");
    }

    /** The from-side column(s), for a {@link RelationshipKind#SELF_REFERENTIAL} mapping. */
    public List<String> fromKeys() {
        return fromKeys;
    }

    /** @throws IllegalStateException if the to-side key is composite */
    public String toKey() {
        return scalar(toKeys, "toKey");
    }

    /** The to-side column(s), for a {@link RelationshipKind#SELF_REFERENTIAL} mapping. */
    public List<String> toKeys() {
        return toKeys;
    }

    /** @throws IllegalStateException if the parent primary key is composite */
    public String parentPrimaryKey() {
        return scalar(parentPrimaryKeys, "parentPrimaryKey");
    }

    /** The parent table's primary key column(s), for a {@link RelationshipKind#ONE_TO_MANY}/{@code MANY_TO_ONE} mapping. */
    public List<String> parentPrimaryKeys() {
        return parentPrimaryKeys;
    }

    /** @throws IllegalStateException if the child foreign key is composite */
    public String childForeignKey() {
        return scalar(childForeignKeys, "childForeignKey");
    }

    /** The child table's foreign key column(s), for a {@link RelationshipKind#ONE_TO_MANY}/{@code MANY_TO_ONE} mapping. */
    public List<String> childForeignKeys() {
        return childForeignKeys;
    }

    /** Cypher relationship property name to column mapping. */
    public Map<String, PropertyMapping> properties() {
        return properties;
    }

    /** @throws IllegalArgumentException if {@code property} has no mapping */
    public String columnForProperty(final String property) {
        final PropertyMapping mapping = properties.get(property);
        if (mapping == null) {
            throw new IllegalArgumentException("No relationship property mapping for property: " + property);
        }
        return mapping.column();
    }

    /** Whether this mapping's {@link #fromLabel()}/{@link #toLabel()} exactly match, in order, {@code from}/{@code to}. */
    public boolean matchesDirectedLabels(final String from, final String to) {
        return fromLabel.equals(from) && toLabel.equals(to);
    }

    /** Whether this mapping matches {@code left}/{@code right} in either direction. */
    public boolean matchesUndirectedLabels(final String left, final String right) {
        return matchesDirectedLabels(left, right) || matchesDirectedLabels(right, left);
    }

    /** Whether the node labeled {@code leftLabel} is this relationship's parent side, given {@code rightLabel} on the other end. */
    public boolean isLeftParent(final String leftLabel, final String rightLabel) {
        return matchesDirectedLabels(leftLabel, rightLabel);
    }

    /** Whether the node labeled {@code rightLabel} is this relationship's parent side, given {@code leftLabel} on the other end. */
    public boolean isRightParent(final String leftLabel, final String rightLabel) {
        return matchesDirectedLabels(rightLabel, leftLabel);
    }

    /** The columns a {@link RelationshipKind#JOIN_TABLE} relationship projects when returned by variable. */
    public List<String> joinTableProjection(final String joinAlias) {
        return List.of(joinAlias + ".*");
    }

    /** The columns a {@link RelationshipKind#SELF_REFERENTIAL} relationship projects when returned by variable. */
    public List<String> selfReferentialProjection(final String leftAlias, final String rightAlias) {
        return pairProjection(leftAlias, fromKeys, rightAlias, toKeys);
    }

    /** The columns a one-to-many relationship projects when returned by variable, with the parent on the left. */
    public List<String> oneToManyProjectionForLeftParent(final String leftAlias, final String rightAlias) {
        return pairProjection(rightAlias, childForeignKeys, leftAlias, parentPrimaryKeys);
    }

    /** The columns a one-to-many relationship projects when returned by variable, with the parent on the right. */
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

    private String scalar(final List<String> values, final String name) {
        if (values.size() != 1) {
            throw new IllegalStateException("Composite " + name + " is not scalar.");
        }
        return values.get(0);
    }
}
