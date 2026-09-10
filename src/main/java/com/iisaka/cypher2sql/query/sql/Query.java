package com.iisaka.cypher2sql.query.sql;

/**
 * A SQL statement that can render itself for a given {@link Grammar}.
 *
 * @param <D> the grammar type this query renders for
 */
public interface Query<D extends Grammar> {
    /** Renders this query as SQL text using the given dialect's quoting rules. */
    String render(D grammar);
}
