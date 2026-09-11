package com.iisaka.cypher2sql.query.cypher;

/**
 * One clause of a parsed Cypher query, in the order it appeared in the source text.
 *
 * @see MatchClause
 * @see WithClause
 * @see ReturnClause
 */
public sealed interface Clause permits MatchClause, WithClause, ReturnClause {
}
