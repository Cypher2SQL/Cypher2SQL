package com.iisaka.cypher2sql.schema;

final class TranslationCapabilities {
    private final String rawCypher;
    private final int edgeCount;

    TranslationCapabilities(final String rawCypher, final int edgeCount) {
        this.rawCypher = rawCypher;
        this.edgeCount = edgeCount;
    }

    void ensureSupported() {
        if (hasVariableLengthTraversal()) {
            // Placeholder only: recursive traversal translation is intentionally not implemented yet.
            throw new UnsupportedOperationException(
                    "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.");
        }
        if (edgeCount > 1) {
            // Placeholder only: multi-hop traversal planning is intentionally not implemented yet.
            throw new UnsupportedOperationException(
                    "Multi-hop traversals are not supported yet; traversal planning is a future enhancement.");
        }
    }

    private boolean hasVariableLengthTraversal() {
        return rawCypher != null && rawCypher.contains("[*");
    }
}
