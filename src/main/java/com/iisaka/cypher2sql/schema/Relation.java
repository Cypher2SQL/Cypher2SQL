package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

sealed interface Relation permits JoinTableRelation, SelfRelation, OneToManyRelation {
    void applyTo(SelectQuery select, SchemaDefinition schema, AliasState aliases);

    static Relation from(
            final EdgeMapping edgeMapping,
            final Node left,
            final Node right,
            final String leftAlias,
            final String rightAlias) {
        return switch (edgeMapping.relationshipKind()) {
            case JOIN_TABLE -> new JoinTableRelation(edgeMapping, left, right, leftAlias, rightAlias);
            case SELF_REFERENTIAL -> new SelfRelation(edgeMapping, left, right, leftAlias, rightAlias);
            case ONE_TO_MANY -> new OneToManyRelation(edgeMapping, left, right, leftAlias, rightAlias);
        };
    }
}
