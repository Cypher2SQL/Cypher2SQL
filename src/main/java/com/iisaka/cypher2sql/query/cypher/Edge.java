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
}
