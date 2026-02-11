package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CypherPatternExtractor {
    private static final Pattern NODE_PATTERN = Pattern.compile("\\((?<var>\\w+)?(?::(?<label>\\w+))?.*\\)");
    private static final Pattern REL_PATTERN = Pattern.compile("\\[(?<var>\\w+)?(?::(?<type>\\w+))?.*\\]");

    private CypherPatternExtractor() {
    }

    public static List<CypherPattern> extract(final Parser parser, final ParseTree parseTree) {
        final ParserRuleContext patternRoot = findFirstPatternElement(parser, parseTree);
        if (patternRoot == null) {
            return List.of();
        }
        final List<ParserRuleContext> nodeContexts = new ArrayList<>();
        final List<ParserRuleContext> relContexts = new ArrayList<>();
        collectPatternPieces(parser, patternRoot, nodeContexts, relContexts);

        if (nodeContexts.isEmpty()) {
            return List.of();
        }

        final List<CypherNode> nodes = new ArrayList<>();
        for (final ParserRuleContext nodeContext : nodeContexts) {
            nodes.add(parseNode(nodeContext.getText()));
        }

        final List<CypherEdge> edges = new ArrayList<>();
        for (final ParserRuleContext relContext : relContexts) {
            edges.add(parseEdge(relContext.getText()));
        }

        return List.of(new CypherPattern(nodes, edges));
    }

    private static ParserRuleContext findFirstPatternElement(final Parser parser, final ParseTree parseTree) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleName(parser, context);
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

    private static void collectPatternPieces(
            final Parser parser,
            final ParseTree root,
            final List<ParserRuleContext> nodes,
            final List<ParserRuleContext> relationships) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleName(parser, context);
                if ("nodePattern".equals(ruleName)) {
                    nodes.add(context);
                } else if ("relationshipPattern".equals(ruleName)) {
                    relationships.add(context);
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
    }

    private static String ruleName(final Parser parser, final ParserRuleContext context) {
        return parser.getRuleNames()[context.getRuleIndex()];
    }

    private static CypherNode parseNode(final String text) {
        final Matcher matcher = NODE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported node pattern: " + text);
        }
        final String variable = matcher.group("var");
        if (variable == null || variable.isBlank()) {
            throw new IllegalArgumentException("Node pattern missing variable: " + text);
        }
        final String label = matcher.group("label");
        return new CypherNode(variable, label);
    }

    private static CypherEdge parseEdge(final String text) {
        final Matcher matcher = REL_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unsupported relationship pattern: " + text);
        }
        final String variable = matcher.group("var");
        final String type = matcher.group("type");
        final CypherEdge.Direction direction;
        if (text.contains("->")) {
            direction = CypherEdge.Direction.LEFT_TO_RIGHT;
        } else if (text.contains("<-")) {
            direction = CypherEdge.Direction.RIGHT_TO_LEFT;
        } else {
            direction = CypherEdge.Direction.UNDIRECTED;
        }
        return new CypherEdge(variable, type, direction);
    }
}
