package com.iisaka.cypher2sql.query.cypher;

public sealed interface Clause permits MatchClause, WithClause, ReturnClause {
}
