package com.iisaka.cypher2sql.schema;

import com.iisaka.cypher2sql.query.cypher.Node;
import com.iisaka.cypher2sql.query.sql.JoinClause;
import com.iisaka.cypher2sql.query.sql.SelectQuery;

final class SelfRelation implements Relation {
    private final EdgeMapping edgeMapping;
    private final Node left;
    private final String leftAlias;
    private final String rightAlias;

    SelfRelation(
            final EdgeMapping edgeMapping,
            final Node left,
            final Node right,
            final String leftAlias,
            final String rightAlias) {
        this.edgeMapping = edgeMapping;
        this.left = left;
        this.leftAlias = leftAlias;
        this.rightAlias = rightAlias;
    }

    @Override
    public EdgeProjection applyTo(final SelectQuery select, final SchemaDefinition schema, final AliasState aliases) {
        final NodeMapping leftMapping = schema.nodeForLabel(left.label());
        final String joinOnSelf = leftAlias + "." + edgeMapping.fromKey()
                + " = " + rightAlias + "." + edgeMapping.toKey();
        select.addJoin(new JoinClause(JoinClause.JoinType.INNER, leftMapping.table(), rightAlias, joinOnSelf));
        return new EdgeProjection(java.util.List.of(
                leftAlias + "." + edgeMapping.fromKey(),
                rightAlias + "." + edgeMapping.toKey()));
    }
}
