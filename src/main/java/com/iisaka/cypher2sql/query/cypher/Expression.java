package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface Expression permits Expression.VariableExpression,
        Expression.PropertyExpression,
        Expression.ConstantExpression,
        Expression.FunctionExpression,
        Expression.BinaryExpression,
        Expression.UnaryExpression,
        Expression.CaseExpression,
        Expression.WildcardExpression {

    static Expression parse(final String raw) {
        return new Parser(raw).parse();
    }

    static Expression parse(final Cypher25Parser.ExpressionContext expression) {
        return new TreeParser().parseExpression(expression);
    }

    enum Operator {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        MODULO("%"),
        EQUALS("="),
        NOT_EQUALS("<>"),
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL("<="),
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL(">="),
        AND("AND"),
        OR("OR"),
        NOT("NOT"),
        NEGATE("-"),
        POSITIVE("+");

        private final String sql;

        Operator(final String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }

    record VariableExpression(String name) implements Expression {
    }

    record PropertyExpression(Expression receiver, String property) implements Expression {
    }

    record ConstantExpression(Object value) implements Expression {
    }

    record FunctionExpression(String name, List<Expression> arguments) implements Expression {
    }

    record BinaryExpression(Expression left, Operator operator, Expression right) implements Expression {
    }

    record UnaryExpression(Operator operator, Expression operand) implements Expression {
    }

    record CaseExpression(
            Expression subject,
            List<WhenThen> whenThens,
            Expression elseExpression) implements Expression {
        public record WhenThen(Expression whenExpression, Expression thenExpression) {
        }
    }

    enum WildcardExpression implements Expression {
        INSTANCE
    }

    final class TreeParser {
        private static final Map<String, Operator> COMPARISON_OPERATORS = Map.of(
                "=", Operator.EQUALS,
                "<>", Operator.NOT_EQUALS,
                "!=", Operator.NOT_EQUALS,
                "<=", Operator.LESS_THAN_OR_EQUAL,
                ">=", Operator.GREATER_THAN_OR_EQUAL,
                "<", Operator.LESS_THAN,
                ">", Operator.GREATER_THAN);
        private static final Map<String, Operator> ADDITIVE_OPERATORS = Map.of(
                "+", Operator.ADD,
                "-", Operator.SUBTRACT);
        private static final Map<String, Operator> MULTIPLICATIVE_OPERATORS = Map.of(
                "*", Operator.MULTIPLY,
                "/", Operator.DIVIDE,
                "%", Operator.MODULO);

        Expression parseExpression(final Cypher25Parser.ExpressionContext context) {
            Expression expression = parseExpression11(context.expression11(0));
            for (int i = 1; i < context.expression11().size(); i++) {
                expression = new BinaryExpression(expression, Operator.OR, parseExpression11(context.expression11(i)));
            }
            return expression;
        }

        private Expression parseExpression11(final Cypher25Parser.Expression11Context context) {
            if (!context.XOR().isEmpty()) {
                throw unsupported(context);
            }
            Expression expression = parseExpression10(context.expression10(0));
            for (int i = 1; i < context.expression10().size(); i++) {
                expression = new BinaryExpression(expression, Operator.AND, parseExpression10(context.expression10(i)));
            }
            return expression;
        }

        private Expression parseExpression10(final Cypher25Parser.Expression10Context context) {
            Expression expression = parseExpression9(context.expression9(0));
            for (int i = 1; i < context.expression9().size(); i++) {
                expression = new BinaryExpression(expression, Operator.AND, parseExpression9(context.expression9(i)));
            }
            return expression;
        }

        private Expression parseExpression9(final Cypher25Parser.Expression9Context context) {
            Expression expression = parseExpression8(context.expression8());
            if (context.NOT().size() % 2 == 1) {
                return new UnaryExpression(Operator.NOT, expression);
            }
            return expression;
        }

        private Expression parseExpression8(final Cypher25Parser.Expression8Context context) {
            Expression expression = parseExpression7(context.expression7(0));
            int operandIndex = 1;
            for (int i = 0; i < context.getChildCount(); i++) {
                final ParseTree child = context.getChild(i);
                if (child instanceof TerminalNode terminal) {
                    final Operator operator = COMPARISON_OPERATORS.get(terminal.getText());
                    if (operator != null) {
                        if (operandIndex >= context.expression7().size()) {
                            throw unsupported(context);
                        }
                        expression = new BinaryExpression(
                                expression,
                                operator,
                                parseExpression7(context.expression7(operandIndex++)));
                    }
                }
            }
            return expression;
        }

        private Expression parseExpression7(final Cypher25Parser.Expression7Context context) {
            if (context.comparisonExpression6() != null) {
                throw unsupported(context);
            }
            return parseExpression6(context.expression6());
        }

        private Expression parseExpression6(final Cypher25Parser.Expression6Context context) {
            return parseLeftAssociative(
                    context,
                    context.expression5(),
                    operand -> parseExpression5((Cypher25Parser.Expression5Context) operand),
                    ADDITIVE_OPERATORS);
        }

        private Expression parseExpression5(final Cypher25Parser.Expression5Context context) {
            return parseLeftAssociative(
                    context,
                    context.expression4(),
                    operand -> parseExpression4((Cypher25Parser.Expression4Context) operand),
                    MULTIPLICATIVE_OPERATORS);
        }

        private Expression parseExpression4(final Cypher25Parser.Expression4Context context) {
            if (!context.POW().isEmpty()) {
                throw unsupported(context);
            }
            return parseExpression3(context.expression3(0));
        }

        private Expression parseExpression3(final Cypher25Parser.Expression3Context context) {
            Expression expression = parseExpression2(context.expression2());
            for (int i = context.getChildCount() - 2; i >= 0; i--) {
                final ParseTree child = context.getChild(i);
                if (child instanceof TerminalNode terminal) {
                    if ("-".equals(terminal.getText())) {
                        expression = new UnaryExpression(Operator.NEGATE, expression);
                    } else if ("+".equals(terminal.getText())) {
                        expression = new UnaryExpression(Operator.POSITIVE, expression);
                    }
                }
            }
            return expression;
        }

        private Expression parseExpression2(final Cypher25Parser.Expression2Context context) {
            Expression expression = parseExpression1(context.expression1());
            for (final Cypher25Parser.PostFixContext postFix : context.postFix()) {
                if (postFix instanceof Cypher25Parser.PropertyPostfixContext propertyPostfix) {
                    expression = new PropertyExpression(
                            expression,
                            propertyPostfix.property().propertyKeyName().getText());
                    continue;
                }
                throw unsupported(postFix);
            }
            return expression;
        }

        private Expression parseExpression1(final Cypher25Parser.Expression1Context context) {
            if (context.parenthesizedExpression() != null) {
                return parseExpression(context.parenthesizedExpression().expression());
            }
            if (context.variable() != null) {
                return new VariableExpression(context.variable().getText());
            }
            if (context.functionInvocation() != null) {
                return parseFunctionInvocation(context.functionInvocation());
            }
            if (context.countStar() != null) {
                return new FunctionExpression("count", List.of(WildcardExpression.INSTANCE));
            }
            if (context.literal() != null) {
                return parseLiteral(context.literal());
            }
            if (context.caseExpression() != null) {
                return parseCaseExpression(context.caseExpression());
            }
            if (context.extendedCaseExpression() != null) {
                throw unsupported(context.extendedCaseExpression());
            }
            throw unsupported(context);
        }

        private Expression parseFunctionInvocation(final Cypher25Parser.FunctionInvocationContext context) {
            if (context.DISTINCT() != null) {
                throw unsupported(context);
            }
            final List<Expression> arguments = new ArrayList<>();
            for (final Cypher25Parser.FunctionArgumentContext argument : context.functionArgument()) {
                arguments.add(parseExpression(argument.expression()));
            }
            return new FunctionExpression(context.functionName().getText(), List.copyOf(arguments));
        }

        private Expression parseCaseExpression(final Cypher25Parser.CaseExpressionContext context) {
            Expression subject = null;
            Expression elseExpression = null;
            boolean sawElse = false;
            for (int i = 0; i < context.getChildCount(); i++) {
                final ParseTree child = context.getChild(i);
                if (child instanceof TerminalNode terminal) {
                    if ("ELSE".equals(terminal.getText())) {
                        sawElse = true;
                    }
                    continue;
                }
                if (child instanceof Cypher25Parser.CaseAlternativeContext) {
                    continue;
                }
                if (child instanceof Cypher25Parser.ExpressionContext expressionContext) {
                    if (sawElse) {
                        elseExpression = parseExpression(expressionContext);
                    } else if (subject == null) {
                        subject = parseExpression(expressionContext);
                    } else {
                        throw unsupported(context);
                    }
                }
            }

            final List<CaseExpression.WhenThen> whenThens = new ArrayList<>();
            for (final Cypher25Parser.CaseAlternativeContext alternative : context.caseAlternative()) {
                whenThens.add(new CaseExpression.WhenThen(
                        parseExpression(alternative.expression(0)),
                        parseExpression(alternative.expression(1))));
            }
            return new CaseExpression(subject, List.copyOf(whenThens), elseExpression);
        }

        private Expression parseLiteral(final Cypher25Parser.LiteralContext context) {
            if (context instanceof Cypher25Parser.NumericLiteralContext numericLiteral) {
                return new ConstantExpression(parseNumber(numericLiteral.numberLiteral().getText()));
            }
            if (context instanceof Cypher25Parser.StringsLiteralContext stringsLiteral) {
                return new ConstantExpression(parseStringLiteral(stringsLiteral.stringLiteral().getText()));
            }
            if (context instanceof Cypher25Parser.BooleanLiteralContext booleanLiteral) {
                return new ConstantExpression(booleanLiteral.TRUE() != null);
            }
            if (context instanceof Cypher25Parser.KeywordLiteralContext keywordLiteral) {
                if (keywordLiteral.NULL() != null) {
                    return new ConstantExpression(null);
                }
            }
            throw unsupported(context);
        }

        private Expression parseLeftAssociative(
                final ParserRuleContext context,
                final List<? extends ParserRuleContext> operands,
                final java.util.function.Function<ParserRuleContext, Expression> parser,
                final Map<String, Operator> operators) {
            Expression expression = parser.apply(operands.get(0));
            int operandIndex = 1;
            for (int i = 0; i < context.getChildCount(); i++) {
                final ParseTree child = context.getChild(i);
                if (child instanceof TerminalNode terminal) {
                    final Operator operator = operators.get(terminal.getText());
                    if (operator != null) {
                        if (operandIndex >= operands.size()) {
                            throw unsupported(context);
                        }
                        expression = new BinaryExpression(expression, operator, parser.apply(operands.get(operandIndex++)));
                    }
                }
            }
            return expression;
        }

        private Object parseNumber(final String text) {
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

        private String parseStringLiteral(final String text) {
            if (text.length() < 2) {
                return text;
            }
            final char quote = text.charAt(0);
            final String body = text.substring(1, text.length() - 1);
            final String doubled = String.valueOf(quote) + quote;
            return body.replace(doubled, String.valueOf(quote));
        }

        private IllegalArgumentException unsupported(final ParseTree tree) {
            return new IllegalArgumentException("Unsupported expression: " + tree.getText());
        }
    }

    final class Parser {
        private final List<Token> tokens;
        private int position;

        Parser(final String raw) {
            this.tokens = tokenize(raw);
        }

        Expression parse() {
            final Expression expression = parseCaseOrLogical();
            expect(TokenType.EOF);
            return expression;
        }

        private Expression parseCaseOrLogical() {
            if (matchKeyword("CASE")) {
                return parseCase();
            }
            return parseOr();
        }

        private Expression parseCase() {
            Expression subject = null;
            if (!peekKeyword("WHEN")) {
                subject = parseOr();
            }
            final List<CaseExpression.WhenThen> whenThens = new ArrayList<>();
            do {
                expectKeyword("WHEN");
                final Expression whenExpression = parseOr();
                expectKeyword("THEN");
                final Expression thenExpression = parseCaseOrLogical();
                whenThens.add(new CaseExpression.WhenThen(whenExpression, thenExpression));
            } while (peekKeyword("WHEN"));
            Expression elseExpression = null;
            if (matchKeyword("ELSE")) {
                elseExpression = parseCaseOrLogical();
            }
            expectKeyword("END");
            return new CaseExpression(subject, List.copyOf(whenThens), elseExpression);
        }

        private Expression parseOr() {
            Expression expression = parseAnd();
            while (matchKeyword("OR")) {
                expression = new BinaryExpression(expression, Operator.OR, parseAnd());
            }
            return expression;
        }

        private Expression parseAnd() {
            Expression expression = parseComparison();
            while (matchKeyword("AND")) {
                expression = new BinaryExpression(expression, Operator.AND, parseComparison());
            }
            return expression;
        }

        private Expression parseComparison() {
            Expression expression = parseAdditive();
            while (true) {
                if (matchSymbol("=")) {
                    expression = new BinaryExpression(expression, Operator.EQUALS, parseAdditive());
                } else if (matchSymbol("<>") || matchSymbol("!=")) {
                    expression = new BinaryExpression(expression, Operator.NOT_EQUALS, parseAdditive());
                } else if (matchSymbol("<=")) {
                    expression = new BinaryExpression(expression, Operator.LESS_THAN_OR_EQUAL, parseAdditive());
                } else if (matchSymbol(">=")) {
                    expression = new BinaryExpression(expression, Operator.GREATER_THAN_OR_EQUAL, parseAdditive());
                } else if (matchSymbol("<")) {
                    expression = new BinaryExpression(expression, Operator.LESS_THAN, parseAdditive());
                } else if (matchSymbol(">")) {
                    expression = new BinaryExpression(expression, Operator.GREATER_THAN, parseAdditive());
                } else {
                    return expression;
                }
            }
        }

        private Expression parseAdditive() {
            Expression expression = parseMultiplicative();
            while (true) {
                if (matchSymbol("+")) {
                    expression = new BinaryExpression(expression, Operator.ADD, parseMultiplicative());
                } else if (matchSymbol("-")) {
                    expression = new BinaryExpression(expression, Operator.SUBTRACT, parseMultiplicative());
                } else {
                    return expression;
                }
            }
        }

        private Expression parseMultiplicative() {
            Expression expression = parseUnary();
            while (true) {
                if (matchSymbol("*")) {
                    expression = new BinaryExpression(expression, Operator.MULTIPLY, parseUnary());
                } else if (matchSymbol("/")) {
                    expression = new BinaryExpression(expression, Operator.DIVIDE, parseUnary());
                } else if (matchSymbol("%")) {
                    expression = new BinaryExpression(expression, Operator.MODULO, parseUnary());
                } else {
                    return expression;
                }
            }
        }

        private Expression parseUnary() {
            if (matchKeyword("NOT")) {
                return new UnaryExpression(Operator.NOT, parseUnary());
            }
            if (matchSymbol("-")) {
                return new UnaryExpression(Operator.NEGATE, parseUnary());
            }
            if (matchSymbol("+")) {
                return new UnaryExpression(Operator.POSITIVE, parseUnary());
            }
            return parsePostfix();
        }

        private Expression parsePostfix() {
            Expression expression = parsePrimary();
            while (matchSymbol(".")) {
                final Token property = expect(TokenType.IDENTIFIER);
                expression = new PropertyExpression(expression, property.text());
            }
            return expression;
        }

        private Expression parsePrimary() {
            if (matchSymbol("(")) {
                final Expression expression = parseCaseOrLogical();
                expectSymbol(")");
                return expression;
            }
            if (matchSymbol("*")) {
                return WildcardExpression.INSTANCE;
            }
            final Token token = peek();
            if (token.type() == TokenType.NUMBER) {
                advance();
                return new ConstantExpression(parseNumber(token.text()));
            }
            if (token.type() == TokenType.STRING) {
                advance();
                return new ConstantExpression(token.text());
            }
            if (token.type() == TokenType.KEYWORD) {
                if ("TRUE".equals(token.text())) {
                    advance();
                    return new ConstantExpression(Boolean.TRUE);
                }
                if ("FALSE".equals(token.text())) {
                    advance();
                    return new ConstantExpression(Boolean.FALSE);
                }
                if ("NULL".equals(token.text())) {
                    advance();
                    return new ConstantExpression(null);
                }
            }
            final Token identifier = expect(TokenType.IDENTIFIER);
            if (matchSymbol("(")) {
                final List<Expression> arguments = new ArrayList<>();
                if (!matchSymbol(")")) {
                    do {
                        arguments.add(parseCaseOrLogical());
                    } while (matchSymbol(","));
                    expectSymbol(")");
                }
                return new FunctionExpression(identifier.text(), List.copyOf(arguments));
            }
            return new VariableExpression(identifier.text());
        }

        private Object parseNumber(final String text) {
            if (text.contains(".")) {
                return Double.valueOf(text);
            }
            return Long.valueOf(text);
        }

        private boolean matchKeyword(final String keyword) {
            if (peekKeyword(keyword)) {
                advance();
                return true;
            }
            return false;
        }

        private boolean peekKeyword(final String keyword) {
            final Token token = peek();
            return token.type() == TokenType.KEYWORD && keyword.equals(token.text());
        }

        private boolean matchSymbol(final String symbol) {
            final Token token = peek();
            if (token.type() == TokenType.SYMBOL && symbol.equals(token.text())) {
                advance();
                return true;
            }
            return false;
        }

        private void expectKeyword(final String keyword) {
            if (!matchKeyword(keyword)) {
                throw unsupported();
            }
        }

        private void expectSymbol(final String symbol) {
            if (!matchSymbol(symbol)) {
                throw unsupported();
            }
        }

        private Token expect(final TokenType type) {
            final Token token = peek();
            if (token.type() != type) {
                throw unsupported();
            }
            advance();
            return token;
        }

        private Token peek() {
            return tokens.get(position);
        }

        private void advance() {
            position++;
        }

        private IllegalArgumentException unsupported() {
            final StringBuilder remaining = new StringBuilder();
            for (int i = position; i < tokens.size() && tokens.get(i).type() != TokenType.EOF; i++) {
                remaining.append(tokens.get(i).raw());
            }
            return new IllegalArgumentException("Unsupported RETURN expression: " + remaining);
        }

        private static List<Token> tokenize(final String raw) {
            final List<Token> tokens = new ArrayList<>();
            int i = 0;
            while (i < raw.length()) {
                final char c = raw.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (c == '\'') {
                    final StringBuilder value = new StringBuilder();
                    i++;
                    while (i < raw.length()) {
                        final char current = raw.charAt(i);
                        if (current == '\'' && i + 1 < raw.length() && raw.charAt(i + 1) == '\'') {
                            value.append('\'');
                            i += 2;
                            continue;
                        }
                        if (current == '\'') {
                            i++;
                            break;
                        }
                        value.append(current);
                        i++;
                    }
                    tokens.add(new Token(TokenType.STRING, value.toString(), "'" + value + "'"));
                    continue;
                }
                if (Character.isDigit(c)) {
                    final int start = i;
                    i++;
                    while (i < raw.length() && (Character.isDigit(raw.charAt(i)) || raw.charAt(i) == '.')) {
                        i++;
                    }
                    final String text = raw.substring(start, i);
                    tokens.add(new Token(TokenType.NUMBER, text, text));
                    continue;
                }
                if (Character.isLetter(c) || c == '_') {
                    final int start = i;
                    i++;
                    while (i < raw.length() && (Character.isLetterOrDigit(raw.charAt(i)) || raw.charAt(i) == '_')) {
                        i++;
                    }
                    final String text = raw.substring(start, i);
                    final String upper = text.toUpperCase();
                    if (List.of("AND", "OR", "NOT", "CASE", "WHEN", "THEN", "ELSE", "END", "TRUE", "FALSE", "NULL")
                            .contains(upper)) {
                        tokens.add(new Token(TokenType.KEYWORD, upper, text));
                    } else {
                        tokens.add(new Token(TokenType.IDENTIFIER, text, text));
                    }
                    continue;
                }
                if (i + 1 < raw.length()) {
                    final String two = raw.substring(i, i + 2);
                    if (List.of("<=", ">=", "<>", "!=").contains(two)) {
                        tokens.add(new Token(TokenType.SYMBOL, two, two));
                        i += 2;
                        continue;
                    }
                }
                final String one = String.valueOf(c);
                if (List.of("(", ")", ",", ".", "+", "-", "*", "/", "%", "=", "<", ">").contains(one)) {
                    tokens.add(new Token(TokenType.SYMBOL, one, one));
                    i++;
                    continue;
                }
                throw new IllegalArgumentException("Unsupported RETURN expression: " + raw);
            }
            tokens.add(new Token(TokenType.EOF, "", ""));
            return List.copyOf(tokens);
        }

        private enum TokenType {
            IDENTIFIER,
            NUMBER,
            STRING,
            KEYWORD,
            SYMBOL,
            EOF
        }

        private record Token(TokenType type, String text, String raw) {
        }
    }
}
