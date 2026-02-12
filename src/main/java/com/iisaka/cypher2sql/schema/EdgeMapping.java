package com.iisaka.cypher2sql.schema;

public final class EdgeMapping {
    public enum RelationshipKind {
        JOIN_TABLE,
        SELF_REFERENTIAL,
        ONE_TO_MANY
    }

    private final String type;
    private final String fromLabel;
    private final String toLabel;
    private final RelationshipKind relationshipKind;

    private final String joinTable;
    private final String fromJoinKey;
    private final String toJoinKey;

    private final String fromKey;
    private final String toKey;

    private final String parentPrimaryKey;
    private final String childForeignKey;

    private EdgeMapping(
            final String type,
            final String fromLabel,
            final String toLabel,
            final RelationshipKind relationshipKind,
            final String joinTable,
            final String fromJoinKey,
            final String toJoinKey,
            final String fromKey,
            final String toKey,
            final String parentPrimaryKey,
            final String childForeignKey) {
        this.type = type;
        this.fromLabel = fromLabel;
        this.toLabel = toLabel;
        this.relationshipKind = relationshipKind;
        this.joinTable = joinTable;
        this.fromJoinKey = fromJoinKey;
        this.toJoinKey = toJoinKey;
        this.fromKey = fromKey;
        this.toKey = toKey;
        this.parentPrimaryKey = parentPrimaryKey;
        this.childForeignKey = childForeignKey;
    }

    public static EdgeMapping forJoinTable(
            final String type,
            final String fromLabel,
            final String toLabel,
            final String joinTable,
            final String fromJoinKey,
            final String toJoinKey) {
        return new EdgeMapping(type, fromLabel, toLabel, RelationshipKind.JOIN_TABLE,
                joinTable, fromJoinKey, toJoinKey, null, null, null, null);
    }

    public static EdgeMapping forSelfReferential(
            final String type,
            final String label,
            final String fromKey,
            final String toKey) {
        return new EdgeMapping(type, label, label, RelationshipKind.SELF_REFERENTIAL,
                null, null, null, fromKey, toKey, null, null);
    }

    public static EdgeMapping forOneToMany(
            final String type,
            final String parentLabel,
            final String childLabel,
            final String parentPrimaryKey,
            final String childForeignKey) {
        return new EdgeMapping(type, parentLabel, childLabel, RelationshipKind.ONE_TO_MANY,
                null, null, null, null, null, parentPrimaryKey, childForeignKey);
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

    public String joinTable() {
        return joinTable;
    }

    public String fromJoinKey() {
        return fromJoinKey;
    }

    public String toJoinKey() {
        return toJoinKey;
    }

    public String fromKey() {
        return fromKey;
    }

    public String toKey() {
        return toKey;
    }

    public String parentPrimaryKey() {
        return parentPrimaryKey;
    }

    public String childForeignKey() {
        return childForeignKey;
    }
}
