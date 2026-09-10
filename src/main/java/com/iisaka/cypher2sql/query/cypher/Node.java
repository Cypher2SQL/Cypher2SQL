package com.iisaka.cypher2sql.query.cypher;

import java.util.Objects;

/**
 * A single node within a Cypher pattern, e.g. {@code (p:Person)}.
 * Either {@link #variable()} or {@link #label()} may be {@code null} for an anonymous or unlabeled node.
 */
public final class Node {
    private final String variable;
    private final String label;

    public Node(final String variable, final String label) {
        this.variable = variable;
        this.label = label;
    }

    /** The node's bound variable name, or {@code null} if anonymous. */
    public String variable() {
        return variable;
    }

    /** The node's label, or {@code null} if unlabeled (label may be inferred later from an adjacent edge). */
    public String label() {
        return label;
    }

    /** Whether this node was written without a variable, e.g. {@code (:Person)}. */
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
        final String variable = PatternText.emptyToNull((colon >= 0 ? inside.substring(0, colon) : inside).trim());
        final String label = colon >= 0 ? firstLabel(inside.substring(colon + 1)) : null;
        return new Node(variable, label);
    }

    private static String firstLabel(final String labelSegment) {
        final int separator = PatternText.indexOfAny(labelSegment, '&', ':', '{', ' ', '\t', '\n', '\r');
        final String label = (separator >= 0 ? labelSegment.substring(0, separator) : labelSegment).trim();
        return label.isEmpty() ? null : label;
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
