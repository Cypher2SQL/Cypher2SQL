package com.iisaka.cypher2sql.query.cypher.expression;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code CASE} expression, in either its subject form ({@code CASE p.status WHEN ... }) or generic form
 * ({@code CASE WHEN ... }).
 *
 * @param subject       the subject expression for a subject-form {@code CASE}, or {@code null} for the generic form
 * @param whenThens     the {@code WHEN}/{@code THEN} branches, in order
 * @param elseExpression the {@code ELSE} expression, or {@code null} if absent
 */
public record CaseExpression(
        Expression subject,
        List<WhenThen> whenThens,
        Expression elseExpression) implements Expression {

    /** One {@code WHEN}/{@code THEN} branch of a {@link CaseExpression}. */
    public record WhenThen(Expression whenExpression, Expression thenExpression) {
    }

    @Override
    public boolean isAggregate() {
        return (subject != null && subject.isAggregate())
                || whenThens.stream()
                        .anyMatch(whenThen -> whenThen.whenExpression().isAggregate() || whenThen.thenExpression().isAggregate())
                || (elseExpression != null && elseExpression.isAggregate());
    }

    static Expression from(final Cypher25Parser.CaseExpressionContext context) {
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
                    elseExpression = Expression.parse(expressionContext);
                } else if (subject == null) {
                    subject = Expression.parse(expressionContext);
                } else {
                    throw Expression.unsupported(context);
                }
            }
        }

        final List<WhenThen> whenThens = new ArrayList<>();
        for (final Cypher25Parser.CaseAlternativeContext alternative : context.caseAlternative()) {
            whenThens.add(new WhenThen(
                    Expression.parse(alternative.expression(0)),
                    Expression.parse(alternative.expression(1))));
        }
        return new CaseExpression(subject, List.copyOf(whenThens), elseExpression);
    }

    static Expression from(final Cypher25Parser.ExtendedCaseExpressionContext context) {
        final Expression subject = Expression.parse(context.expression(0));

        final List<WhenThen> whenThens = new ArrayList<>();
        for (final Cypher25Parser.ExtendedCaseAlternativeContext alternative : context.extendedCaseAlternative()) {
            final List<Cypher25Parser.ExtendedWhenContext> whens = alternative.extendedWhen();
            if (whens.size() != 1 || !(whens.get(0) instanceof Cypher25Parser.WhenEqualsContext whenEquals)) {
                throw Expression.unsupported(alternative);
            }
            whenThens.add(new WhenThen(
                    Expression.parse(whenEquals.expression()),
                    Expression.parse(alternative.expression())));
        }

        final Expression elseExpression = context.elseExp != null ? Expression.parse(context.elseExp) : null;
        return new CaseExpression(subject, List.copyOf(whenThens), elseExpression);
    }
}
