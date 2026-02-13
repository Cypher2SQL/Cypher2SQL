package com.iisaka.cypher2sql.schema;

final class TranslationCapabilities {
    private final String rawCypher;

    TranslationCapabilities(final String rawCypher) {
        this.rawCypher = rawCypher;
    }

    void ensureSupported() {
        if (hasVariableLengthTraversal()) {
            // Placeholder only: recursive traversal translation is intentionally not implemented yet.
            throw new UnsupportedOperationException(
                    "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.");
        }
    }

    private boolean hasVariableLengthTraversal() {
        return rawCypher != null && rawCypher.contains("[*");
    }
}
