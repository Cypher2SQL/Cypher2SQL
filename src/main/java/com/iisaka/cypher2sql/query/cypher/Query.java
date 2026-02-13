package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class Query {
    private final String raw;
    private final ParseTree parseTree;
    private final String[] ruleNames;
    private List<Pattern> patterns;
    private List<ReturnItem> returnItems;

    private Query(final String raw, final ParseTree parseTree, final String[] ruleNames) {
        this.raw = raw;
        this.parseTree = parseTree;
        this.ruleNames = ruleNames;
    }

    public String raw() {
        return raw;
    }

    public List<Pattern> patterns() {
        if (patterns == null) {
            patterns = Collections.unmodifiableList(extractPatterns());
        }
        return patterns;
    }

    public List<ReturnItem> returnItems() {
        if (returnItems == null) {
            returnItems = Collections.unmodifiableList(extractReturnItems());
        }
        return returnItems;
    }

    public ParseTree parseTree() {
        return parseTree;
    }

    public static Query of(final String cypher) {
        final Syntax.ParsedCypher parsed = Syntax.cypher25().parse(cypher);
        final Query query = new Query(cypher, parsed.parseTree(), parsed.ruleNames());
        // Keep parse-time validation behavior while memoizing parsed projections/patterns.
        query.patterns();
        query.returnItems();
        return query;
    }

    private List<Pattern> extractPatterns() {
        final ParserRuleContext patternRoot = findFirstPatternElement();
        if (patternRoot == null) {
            return List.of();
        }

        final List<ParserRuleContext> nodeContexts = new ArrayList<>();
        final List<ParserRuleContext> relationshipContexts = new ArrayList<>();
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(patternRoot);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleNames[context.getRuleIndex()];
                if ("nodePattern".equals(ruleName)) {
                    nodeContexts.add(context);
                } else if ("relationshipPattern".equals(ruleName)) {
                    relationshipContexts.add(context);
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }

        if (nodeContexts.isEmpty()) {
            return List.of();
        }

        final List<Node> nodes = new ArrayList<>();
        for (final ParserRuleContext nodeContext : nodeContexts) {
            nodes.add(Node.fromPatternText(nodeContext.getText()));
        }

        final List<Edge> edges = new ArrayList<>();
        for (final ParserRuleContext relationshipContext : relationshipContexts) {
            edges.add(Edge.fromPatternText(relationshipContext.getText()));
        }

        return List.of(new Pattern(nodes, edges));
    }

    private ParserRuleContext findFirstPatternElement() {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleNames[context.getRuleIndex()];
                if ("patternElement".equals(ruleName) || "patternPart".equals(ruleName)) {
                    return context;
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
        return null;
    }

    private List<ReturnItem> extractReturnItems() {
        final List<ParserRuleContext> returnItemContexts = new ArrayList<>();
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleNames[context.getRuleIndex()];
                final String normalized = ruleName.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
                if (normalized.endsWith("returnitem") || normalized.endsWith("projectionitem")) {
                    returnItemContexts.add(context);
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }

        final List<ReturnItem> items = new ArrayList<>();
        for (final ParserRuleContext context : returnItemContexts) {
            items.add(ReturnItem.fromProjectionExpression(returnExpressionText(context)));
        }
        return items;
    }

    private String returnExpressionText(final ParserRuleContext context) {
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
}
