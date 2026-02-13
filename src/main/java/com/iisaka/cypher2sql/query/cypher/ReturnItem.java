package com.iisaka.cypher2sql.query.cypher;

public record ReturnItem(String variable, String property) {
    static ReturnItem fromProjectionExpression(final String expr) {
        final int dot = expr.indexOf('.');
        if (dot < 0) {
            if (isIdentifier(expr)) {
                return new ReturnItem(expr, null);
            }
            throw unsupported(expr);
        }

        if (expr.indexOf('.', dot + 1) >= 0) {
            throw unsupported(expr);
        }
        final String variable = expr.substring(0, dot);
        final String property = expr.substring(dot + 1);
        if (isIdentifier(variable) && isIdentifier(property)) {
            return new ReturnItem(variable, property);
        }
        throw unsupported(expr);
    }

    private static boolean isIdentifier(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        final char first = value.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException unsupported(final String expr) {
        return new IllegalArgumentException(
                "Unsupported RETURN expression: " + expr + ". Only variable or variable.property are supported.");
    }
}
