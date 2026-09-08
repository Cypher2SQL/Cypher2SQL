package com.iisaka.cypher2sql.query.cypher;

import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.List;

public record ReturnClause(
        List<ProjectionItem> items,
        boolean distinct,
        List<OrderItem> orderItems,
        Long skip,
        Long limit,
        Cypher25Parser.ReturnClauseContext parseTreeNode) implements Clause {
}
