package com.iisaka.cypher2sql.query.cypher;

import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.List;

/**
 * A parsed {@code RETURN} clause.
 *
 * @param items         the projected expressions
 * @param distinct      whether {@code DISTINCT} was specified
 * @param orderItems    this clause's own {@code ORDER BY} keys
 * @param skip          the {@code SKIP} count, or {@code null} if absent
 * @param limit         the {@code LIMIT} count, or {@code null} if absent
 * @param parseTreeNode the originating ANTLR parse-tree node, or {@code null} for a synthesized empty clause
 */
public record ReturnClause(
        List<ProjectionItem> items,
        boolean distinct,
        List<OrderItem> orderItems,
        Long skip,
        Long limit,
        Cypher25Parser.ReturnClauseContext parseTreeNode) implements Clause {
}
