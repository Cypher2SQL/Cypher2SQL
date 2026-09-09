package com.iisaka.cypher2sql.query.cypher;

import java.util.Objects;

public final class Edge {
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

    public enum Direction {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        UNDIRECTED
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
        final String variable = colon >= 0 ? PatternText.emptyToNull(trimmed.substring(0, colon).trim()) : null;
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
        final int separator = PatternText.indexOfAny(typeSegment, '|', '&', ':', '*', '{', ' ', '\t', '\n', '\r');
        final String type = (separator >= 0 ? typeSegment.substring(0, separator) : typeSegment).trim();
        return type.isEmpty() ? null : type;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Edge edge)) {
            return false;
        }
        return Objects.equals(variable, edge.variable)
                && Objects.equals(type, edge.type)
                && direction == edge.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, type, direction);
    }

    @Override
    public String toString() {
        return "Edge[variable=" + variable + ", type=" + type + ", direction=" + direction + "]";
    }
}
