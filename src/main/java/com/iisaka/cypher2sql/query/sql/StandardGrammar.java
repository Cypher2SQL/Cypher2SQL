package com.iisaka.cypher2sql.query.sql;

import java.util.Arrays;

/** The default {@link Grammar}: ANSI SQL double-quoted identifiers, with {@code "} escaped by doubling. */
public final class StandardGrammar implements Grammar {
    @Override
    public String name() {
        return "standard";
    }

    /** @throws IllegalArgumentException if {@code identifier} is {@code null} */
    @Override
    public String quoteIdentifier(final String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier cannot be null");
        }
        return Arrays.stream(identifier.split("\\."))
                .map(part -> "\"" + part.replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining("."));
    }
}
