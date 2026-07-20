package com.iisaka.cypher2sql.query.cypher;

import java.util.Objects;

public final class Node {
    private final String variable;
    private final String label;

    public Node(final String variable, final String label) {
        this.variable = variable;
        this.label = label;
    }

    public String variable() {
        return variable;
    }

    public String label() {
        return label;
    }

    public boolean isAnonymous() {
        return variable == null || variable.isBlank();
    }

    static Node fromPatternText(final String text) {
        final int open = text.indexOf('(');
        final int close = text.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new IllegalArgumentException("Unsupported node pattern: " + text);
        }

        String inside = text.substring(open + 1, close);
        final int propertiesAt = inside.indexOf('{');
        if (propertiesAt >= 0) {
            inside = inside.substring(0, propertiesAt);
        }
        inside = inside.trim();
        if (inside.isEmpty()) {
            return new Node(null, null);
        }

        if (inside.startsWith(":")) {
            return new Node(null, firstLabel(inside.substring(1)));
        }

        final int colon = inside.indexOf(':');
        final String variable = emptyToNull((colon >= 0 ? inside.substring(0, colon) : inside).trim());
        final String label = colon >= 0 ? firstLabel(inside.substring(colon + 1)) : null;
        return new Node(variable, label);
    }

    private static String firstLabel(final String labelSegment) {
        final int separator = indexOfAny(labelSegment, '&', ':', '{', ' ', '\t', '\n', '\r');
        final String label = (separator >= 0 ? labelSegment.substring(0, separator) : labelSegment).trim();
        return label.isEmpty() ? null : label;
    }

    private static int indexOfAny(final String value, final char... needles) {
        int best = -1;
        for (final char needle : needles) {
            final int idx = value.indexOf(needle);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static String emptyToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Node node)) {
            return false;
        }
        return Objects.equals(variable, node.variable) && Objects.equals(label, node.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, label);
    }

    @Override
    public String toString() {
        return "Node[variable=" + variable + ", label=" + label + "]";
    }
}
