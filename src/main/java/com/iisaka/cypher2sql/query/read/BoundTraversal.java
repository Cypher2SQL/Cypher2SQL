package com.iisaka.cypher2sql.query.read;

import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.sql.JoinClause;
import com.iisaka.cypher2sql.query.sql.SelectQuery;
import com.iisaka.cypher2sql.schema.EdgeMapping;

import java.util.List;

public final class BoundTraversal {
    private final Edge edge;
    private final EdgeMapping mapping;
    private final BoundNode left;
    private final BoundNode right;

    public BoundTraversal(
            final Edge edge,
            final EdgeMapping mapping,
            final BoundNode left,
            final BoundNode right) {
        this.edge = edge;
        this.mapping = mapping;
        this.left = left;
        this.right = right;
    }

    public Edge edge() {
        return edge;
    }

    public EdgeMapping mapping() {
        return mapping;
    }

    public BoundNode left() {
        return left;
    }

    public BoundNode right() {
        return right;
    }

    public List<String> applyTo(final SelectQuery select, final int[] nextJoinAliasCounter, final JoinClause.JoinType joinType) {
        return switch (mapping.relationshipKind()) {
            case JOIN_TABLE -> applyJoinTable(select, nextJoinAliasCounter, joinType);
            case SELF_REFERENTIAL -> applySelfReferential(select, joinType);
            case ONE_TO_MANY, MANY_TO_ONE -> applyOneToMany(select, joinType);
        };
    }

    private List<String> applyJoinTable(final SelectQuery select, final int[] nextJoinAliasCounter, final JoinClause.JoinType joinType) {
        final String joinAlias = nextJoinAlias(nextJoinAliasCounter);
        final String joinOnLeft = String.join(" AND ",
                left.mapping().joinOnColumns(left.alias(), left.mapping().primaryKeys(), joinAlias, mapping.fromJoinKeys()));
        select.addJoin(new JoinClause(joinType, mapping.joinTable(), joinAlias, joinOnLeft));

        final String joinOnRight = String.join(" AND ",
                right.mapping().joinOnColumns(joinAlias, mapping.toJoinKeys(), right.alias(), right.mapping().primaryKeys()));
        select.addJoin(new JoinClause(joinType, right.mapping().table(), right.alias(), joinOnRight));
        return mapping.joinTableProjection(joinAlias);
    }

    private List<String> applySelfReferential(final SelectQuery select, final JoinClause.JoinType joinType) {
        final String joinOn = String.join(" AND ",
                left.mapping().joinOnColumns(left.alias(), mapping.fromKeys(), right.alias(), mapping.toKeys()));
        select.addJoin(new JoinClause(joinType, left.mapping().table(), right.alias(), joinOn));
        return mapping.selfReferentialProjection(left.alias(), right.alias());
    }

    private List<String> applyOneToMany(final SelectQuery select, final JoinClause.JoinType joinType) {
        if (mapping.isLeftParent(left.label(), right.label())) {
            final String joinOn = String.join(" AND ",
                    right.mapping().joinOnColumns(right.alias(), mapping.childForeignKeys(), left.alias(), mapping.parentPrimaryKeys()));
            select.addJoin(new JoinClause(joinType, right.mapping().table(), right.alias(), joinOn));
            return mapping.oneToManyProjectionForLeftParent(left.alias(), right.alias());
        }
        if (mapping.isRightParent(left.label(), right.label())) {
            final String joinOn = String.join(" AND ",
                    left.mapping().joinOnColumns(left.alias(), mapping.childForeignKeys(), right.alias(), mapping.parentPrimaryKeys()));
            select.addJoin(new JoinClause(joinType, right.mapping().table(), right.alias(), joinOn));
            return mapping.oneToManyProjectionForRightParent(left.alias(), right.alias());
        }
        throw new IllegalArgumentException("Edge mapping labels do not match nodes: " + mapping.type());
    }

    private String nextJoinAlias(final int[] nextJoinAliasCounter) {
        return "j" + nextJoinAliasCounter[0]++;
    }
}
