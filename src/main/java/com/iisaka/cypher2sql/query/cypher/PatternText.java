package com.iisaka.cypher2sql.query.cypher;

/** Shared text-segment parsing for {@link Node#fromPatternText} and {@link Edge#fromPatternText}. */
final class PatternText {
    private PatternText() {
    }

    static int indexOfAny(final String value, final char... needles) {
        int best = -1;
        for (final char needle : needles) {
            final int idx = value.indexOf(needle);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    static String emptyToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
