package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public record CypherReturnItem(String variable, String property) {
    // Reads RETURN/projection items from the parse tree and normalizes to variable/property form.
    public static List<CypherReturnItem> extract(final Parser parser, final ParseTree parseTree) {
        final List<ParserRuleContext> returnItemContexts = new ArrayList<>();
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = parser.getRuleNames()[context.getRuleIndex()];
                if (isReturnItemRule(ruleName)) {
                    returnItemContexts.add(context);
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }

        final List<CypherReturnItem> items = new ArrayList<>();
        for (final ParserRuleContext context : returnItemContexts) {
            final String expr = returnExpressionText(context);
            items.add(parseProjectionExpression(expr));
        }
        return items;
    }

    private static String returnExpressionText(final ParserRuleContext context) {
        final StringBuilder beforeAlias = new StringBuilder();
        boolean sawAs = false;
        for (int i = 0; i < context.getChildCount(); i++) {
            final ParseTree child = context.getChild(i);
            if (child instanceof TerminalNode terminal && "AS".equalsIgnoreCase(terminal.getText())) {
                sawAs = true;
                break;
            }
            beforeAlias.append(child.getText());
        }
        return (sawAs ? beforeAlias.toString() : context.getText()).trim();
    }

    private static boolean isReturnItemRule(final String ruleName) {
        final String normalized = ruleName.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return normalized.endsWith("returnitem") || normalized.endsWith("projectionitem");
    }

    private static CypherReturnItem parseProjectionExpression(final String expr) {
        final int dot = expr.indexOf('.');
        if (dot < 0) {
            if (isIdentifier(expr)) {
                return new CypherReturnItem(expr, null);
            }
            throw unsupported(expr);
        }

        if (expr.indexOf('.', dot + 1) >= 0) {
            throw unsupported(expr);
        }
        final String variable = expr.substring(0, dot);
        final String property = expr.substring(dot + 1);
        if (isIdentifier(variable) && isIdentifier(property)) {
            return new CypherReturnItem(variable, property);
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
