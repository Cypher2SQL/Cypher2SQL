package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.query.sql.JoinClause;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

final class JoinTableRelation implements Relation {
    private final EdgeMapping edgeMapping;
    private final Node left;
    private final Node right;
    private final String leftAlias;
    private final String rightAlias;

    JoinTableRelation(
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
        final NodeMapping leftMapping = schema.nodeForLabel(left.label());
        final NodeMapping rightMapping = schema.nodeForLabel(right.label());
        final String joinAlias = aliases.nextJoinAlias();

        final String joinOnLeft = leftAlias + "." + leftMapping.primaryKey()
                + " = " + joinAlias + "." + edgeMapping.fromJoinKey();
        select.addJoin(new JoinClause(JoinClause.JoinType.INNER, edgeMapping.joinTable(), joinAlias, joinOnLeft));

        final String joinOnRight = joinAlias + "." + edgeMapping.toJoinKey()
                + " = " + rightAlias + "." + rightMapping.primaryKey();
        select.addJoin(new JoinClause(JoinClause.JoinType.INNER, rightMapping.table(), rightAlias, joinOnRight));
    }
}
