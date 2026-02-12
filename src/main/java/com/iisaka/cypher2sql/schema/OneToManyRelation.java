package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.query.sql.JoinClause;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

final class OneToManyRelation implements Relation {
    private final EdgeMapping edgeMapping;
    private final Node left;
    private final Node right;
    private final String leftAlias;
    private final String rightAlias;

    OneToManyRelation(
            final EdgeMapping edgeMapping,
            final Node left,
            final Node right,
            final String leftAlias,
            final String rightAlias) {
        this.edgeMapping = edgeMapping;
        this.left = left;
        this.right = right;
        this.leftAlias = leftAlias;
        this.rightAlias = rightAlias;
    }

    @Override
    public void applyTo(final SelectQuery select, final SchemaDefinition schema, final AliasState aliases) {
        final NodeMapping rightMapping = schema.nodeForLabel(right.label());
        final String parentLabel = edgeMapping.fromLabel();
        final String childLabel = edgeMapping.toLabel();
        final boolean leftIsParent = left.label().equals(parentLabel) && right.label().equals(childLabel);
        final boolean rightIsParent = right.label().equals(parentLabel) && left.label().equals(childLabel);
        if (leftIsParent) {
            final String joinOn = rightAlias + "." + edgeMapping.childForeignKey()
                    + " = " + leftAlias + "." + edgeMapping.parentPrimaryKey();
            select.addJoin(new JoinClause(JoinClause.JoinType.INNER, rightMapping.table(), rightAlias, joinOn));
            return;
        }
        if (rightIsParent) {
            final String joinOn = leftAlias + "." + edgeMapping.childForeignKey()
                    + " = " + rightAlias + "." + edgeMapping.parentPrimaryKey();
            select.addJoin(new JoinClause(JoinClause.JoinType.INNER, rightMapping.table(), rightAlias, joinOn));
            return;
        }
        throw new IllegalArgumentException("Edge mapping labels do not match nodes: " + edgeMapping.type());
    }
}
