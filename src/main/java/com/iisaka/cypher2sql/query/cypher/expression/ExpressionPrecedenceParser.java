package com.iisaka.cypher2sql.query.cypher.expression;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.List;
import java.util.function.Function;

/**
 * Walks a {@link Cypher25Parser.ExpressionContext}'s eleven operator-precedence levels
 * (expression11, loosest, down to expression1, tightest) and builds the corresponding
 * {@link Expression} tree. The sole implementation detail behind {@link Expression#parse}.
 */
final class ExpressionPrecedenceParser {

    private ExpressionPrecedenceParser() {
    }

    static Expression parse(final Cypher25Parser.ExpressionContext context) {
        Expression expression = parseExpression11(context.expression11(0));
        for (int i = 1; i < context.expression11().size(); i++) {
            expression = new BinaryExpression(expression, Operator.OR, parseExpression11(context.expression11(i)));
        }
        return expression;
    }

    private static Expression parseExpression11(final Cypher25Parser.Expression11Context context) {
        if (!context.XOR().isEmpty()) {
            throw Expression.unsupported(context);
        }
        Expression expression = parseExpression10(context.expression10(0));
        for (int i = 1; i < context.expression10().size(); i++) {
            expression = new BinaryExpression(expression, Operator.AND, parseExpression10(context.expression10(i)));
        }
        return expression;
    }

    private static Expression parseExpression10(final Cypher25Parser.Expression10Context context) {
        Expression expression = parseExpression9(context.expression9(0));
        for (int i = 1; i < context.expression9().size(); i++) {
            expression = new BinaryExpression(expression, Operator.AND, parseExpression9(context.expression9(i)));
        }
        return expression;
    }

    private static Expression parseExpression9(final Cypher25Parser.Expression9Context context) {
        Expression expression = parseExpression8(context.expression8());
        if (context.NOT().size() % 2 == 1) {
            return new UnaryExpression(Operator.NOT, expression);
        }
        return expression;
    }

    private static Expression parseExpression8(final Cypher25Parser.Expression8Context context) {
        Expression expression = parseExpression7(context.expression7(0));
        int operandIndex = 1;
        for (int i = 0; i < context.getChildCount(); i++) {
            final ParseTree child = context.getChild(i);
            if (child instanceof TerminalNode terminal) {
                final Operator operator = comparisonOperator(terminal.getText());
                if (operator != null) {
                    if (operandIndex >= context.expression7().size()) {
                        throw Expression.unsupported(context);
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

    private static Expression parseExpression7(final Cypher25Parser.Expression7Context context) {
        if (context.comparisonExpression6() != null) {
            throw Expression.unsupported(context);
        }
        return parseExpression6(context.expression6());
    }

    private static Expression parseExpression6(final Cypher25Parser.Expression6Context context) {
        return parseLeftAssociative(
                context,
                context.expression5(),
                operand -> parseExpression5((Cypher25Parser.Expression5Context) operand),
                ExpressionPrecedenceParser::additiveOperator);
    }

    private static Expression parseExpression5(final Cypher25Parser.Expression5Context context) {
        return parseLeftAssociative(
                context,
                context.expression4(),
                operand -> parseExpression4((Cypher25Parser.Expression4Context) operand),
                ExpressionPrecedenceParser::multiplicativeOperator);
    }

    private static Expression parseExpression4(final Cypher25Parser.Expression4Context context) {
        if (!context.POW().isEmpty()) {
            throw Expression.unsupported(context);
        }
        return parseExpression3(context.expression3(0));
    }

    private static Expression parseExpression3(final Cypher25Parser.Expression3Context context) {
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

    private static Expression parseExpression2(final Cypher25Parser.Expression2Context context) {
        Expression expression = parseExpression1(context.expression1());
        for (final Cypher25Parser.PostFixContext postFix : context.postFix()) {
            if (postFix instanceof Cypher25Parser.PropertyPostfixContext propertyPostfix) {
                expression = new PropertyExpression(
                        expression,
                        propertyPostfix.property().propertyKeyName().getText());
                continue;
            }
            throw Expression.unsupported(postFix);
        }
        return expression;
    }

    private static Expression parseExpression1(final Cypher25Parser.Expression1Context context) {
        if (context.parenthesizedExpression() != null) {
            return parse(context.parenthesizedExpression().expression());
        }
        if (context.variable() != null) {
            return VariableExpression.from(context.variable());
        }
        if (context.functionInvocation() != null) {
            return FunctionExpression.from(context.functionInvocation());
        }
        if (context.countStar() != null) {
            return new FunctionExpression("count", List.of(WildcardExpression.INSTANCE));
        }
        if (context.literal() != null) {
            return ConstantExpression.from(context.literal());
        }
        if (context.caseExpression() != null) {
            return CaseExpression.from(context.caseExpression());
        }
        if (context.extendedCaseExpression() != null) {
            return CaseExpression.from(context.extendedCaseExpression());
        }
        throw Expression.unsupported(context);
    }

    private static Expression parseLeftAssociative(
            final ParserRuleContext context,
            final List<? extends ParserRuleContext> operands,
            final Function<ParserRuleContext, Expression> parser,
            final Function<String, Operator> operatorLookup) {
        Expression expression = parser.apply(operands.get(0));
        int operandIndex = 1;
        for (int i = 0; i < context.getChildCount(); i++) {
            final ParseTree child = context.getChild(i);
            if (child instanceof TerminalNode terminal) {
                final Operator operator = operatorLookup.apply(terminal.getText());
                if (operator != null) {
                    if (operandIndex >= operands.size()) {
                        throw Expression.unsupported(context);
                    }
                    expression = new BinaryExpression(expression, operator, parser.apply(operands.get(operandIndex++)));
                }
            }
        }
        return expression;
    }

    private static Operator comparisonOperator(final String token) {
        return switch (token) {
            case "=" -> Operator.EQUALS;
            case "<>", "!=" -> Operator.NOT_EQUALS;
            case "<=" -> Operator.LESS_THAN_OR_EQUAL;
            case ">=" -> Operator.GREATER_THAN_OR_EQUAL;
            case "<" -> Operator.LESS_THAN;
            case ">" -> Operator.GREATER_THAN;
            default -> null;
        };
    }

    private static Operator additiveOperator(final String token) {
        return switch (token) {
            case "+" -> Operator.ADD;
            case "-" -> Operator.SUBTRACT;
            default -> null;
        };
    }

    private static Operator multiplicativeOperator(final String token) {
        return switch (token) {
            case "*" -> Operator.MULTIPLY;
            case "/" -> Operator.DIVIDE;
            case "%" -> Operator.MODULO;
            default -> null;
        };
    }
}
