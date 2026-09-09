package com.iisaka.cypher2sql.query.cypher.expression;

import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

public record ConstantExpression(Object value) implements Expression {
    @Override
    public boolean isAggregate() {
        return false;
    }

    static Expression from(final Cypher25Parser.LiteralContext context) {
        if (context instanceof Cypher25Parser.NumericLiteralContext numericLiteral) {
            return new ConstantExpression(parseNumber(numericLiteral.numberLiteral().getText()));
        }
        if (context instanceof Cypher25Parser.StringsLiteralContext stringsLiteral) {
            return new ConstantExpression(parseStringLiteral(stringsLiteral.stringLiteral().getText()));
        }
        if (context instanceof Cypher25Parser.BooleanLiteralContext booleanLiteral) {
            return new ConstantExpression(booleanLiteral.TRUE() != null);
        }
        if (context instanceof Cypher25Parser.KeywordLiteralContext keywordLiteral && keywordLiteral.NULL() != null) {
            return new ConstantExpression(null);
        }
        throw Expression.unsupported(context);
    }

    private static Object parseNumber(final String text) {
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return Long.parseLong(text.substring(2), 16);
        }
        if (text.startsWith("0o") || text.startsWith("0O")) {
            return Long.parseLong(text.substring(2), 8);
        }
        if (text.contains(".")) {
            return Double.valueOf(text);
        }
        return Long.valueOf(text);
    }

    private static String parseStringLiteral(final String text) {
        if (text.length() < 2) {
            return text;
        }
        final char quote = text.charAt(0);
        final String body = text.substring(1, text.length() - 1);
        final String doubled = String.valueOf(quote) + quote;
        return body.replace(doubled, String.valueOf(quote));
    }
}
