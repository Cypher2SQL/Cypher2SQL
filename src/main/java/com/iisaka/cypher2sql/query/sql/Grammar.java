package com.iisaka.cypher2sql.query.sql;

/** A target SQL dialect's identifier-quoting and naming rules, passed to {@link Query#render}. */
public interface Grammar {
    /** The dialect's name, e.g. {@code "standard"}. */
    String name();

    /** Quotes a (possibly dotted, e.g. {@code table.column}) identifier for this dialect. */
    String quoteIdentifier(String identifier);
}
