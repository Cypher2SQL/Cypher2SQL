package com.iisaka.cypher2sql.query.cypher;

public final class Edge {
    public enum Direction {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        UNDIRECTED
    }

    private final String variable;
    private final String type;
    private final Direction direction;

    public Edge(final String variable, final String type, final Direction direction) {
        this.variable = variable;
        this.type = type;
        this.direction = direction;
    }

    public String variable() {
        return variable;
    }

    public String type() {
        return type;
    }

    public Direction direction() {
        return direction;
    }

    static Edge fromPatternText(final String text) {
        final int open = text.indexOf('[');
        final int close = text.lastIndexOf(']');
        if (open < 0 || close <= open) {
            throw new IllegalArgumentException("Unsupported relationship pattern: " + text);
        }
        final String inside = text.substring(open + 1, close).trim();
        final String trimmed = inside.startsWith(":") ? inside.substring(1) : inside;

        final int colon = trimmed.indexOf(':');
        final String variable = colon >= 0 ? emptyToNull(trimmed.substring(0, colon).trim()) : null;
        final String type = colon >= 0
                ? firstType(trimmed.substring(colon + 1))
                : (trimmed.contains("*") ? null : firstType(trimmed));

        final Direction direction;
        if (text.contains("->")) {
            direction = Direction.LEFT_TO_RIGHT;
        } else if (text.contains("<-")) {
            direction = Direction.RIGHT_TO_LEFT;
        } else {
            direction = Direction.UNDIRECTED;
        }
        return new Edge(variable, type, direction);
    }

    private static String firstType(final String typeSegment) {
        final int separator = indexOfAny(typeSegment, '|', '&', ':', '*', '{', ' ', '\t', '\n', '\r');
        final String type = (separator >= 0 ? typeSegment.substring(0, separator) : typeSegment).trim();
        return type.isEmpty() ? null : type;
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
}
