package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class Pattern {
    private final List<Node> nodes;
    private final List<Edge> edges;

    public Pattern(final List<Node> nodes, final List<Edge> edges) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    // Converts ANTLR parse-tree nodes into a stable app-level pattern model.
    public static List<Pattern> extract(final String[] ruleNames, final ParseTree parseTree) {
        final ParserRuleContext patternRoot = findFirstPatternElement(ruleNames, parseTree);
        if (patternRoot == null) {
            return List.of();
        }
        final List<ParserRuleContext> nodeContexts = new ArrayList<>();
        final List<ParserRuleContext> relContexts = new ArrayList<>();
        collectPatternPieces(ruleNames, patternRoot, nodeContexts, relContexts);

        if (nodeContexts.isEmpty()) {
            return List.of();
        }

        final List<Node> nodes = new ArrayList<>();
        for (final ParserRuleContext nodeContext : nodeContexts) {
            nodes.add(parseNode(nodeContext.getText()));
        }

        final List<Edge> edges = new ArrayList<>();
        for (final ParserRuleContext relContext : relContexts) {
            edges.add(parseEdge(relContext.getText()));
        }

        return List.of(new Pattern(nodes, edges));
    }

    private static ParserRuleContext findFirstPatternElement(final String[] ruleNames, final ParseTree parseTree) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleName(ruleNames, context);
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
            final String[] ruleNames,
            final ParseTree root,
            final List<ParserRuleContext> nodes,
            final List<ParserRuleContext> relationships) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleName(ruleNames, context);
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

    private static String ruleName(final String[] ruleNames, final ParserRuleContext context) {
        return ruleNames[context.getRuleIndex()];
    }

    private static Node parseNode(final String text) {
        final int open = text.indexOf('(');
        final int close = text.lastIndexOf(')');
        if (open < 0 || close <= open + 1) {
            throw new IllegalArgumentException("Unsupported node pattern: " + text);
        }
        String inside = text.substring(open + 1, close);
        final int propertiesAt = inside.indexOf('{');
        if (propertiesAt >= 0) {
            inside = inside.substring(0, propertiesAt);
        }
        final int whereAt = inside.toUpperCase().indexOf("WHERE");
        if (whereAt >= 0) {
            inside = inside.substring(0, whereAt);
        }
        inside = inside.trim();

        if (inside.startsWith(":")) {
            throw new IllegalArgumentException("Node pattern missing variable: " + text);
        }

        final int colon = inside.indexOf(':');
        final String variable = (colon >= 0 ? inside.substring(0, colon) : inside).trim();
        if (variable.isEmpty()) {
            throw new IllegalArgumentException("Node pattern missing variable: " + text);
        }

        final String label = colon >= 0 ? firstLabel(inside.substring(colon + 1)) : null;
        return new Node(variable, label);
    }

    private static String firstLabel(final String labelSegment) {
        final int separator = indexOfAny(labelSegment, '&', ':', '{', ' ', '\t', '\n', '\r');
        final String label = (separator >= 0 ? labelSegment.substring(0, separator) : labelSegment).trim();
        return label.isEmpty() ? null : label;
    }

    private static Edge parseEdge(final String text) {
        final int open = text.indexOf('[');
        final int close = text.lastIndexOf(']');
        if (open < 0 || close <= open) {
            throw new IllegalArgumentException("Unsupported relationship pattern: " + text);
        }
        final String inside = text.substring(open + 1, close).trim();
        final String trimmed = inside.startsWith(":") ? inside.substring(1) : inside;

        final int colon = trimmed.indexOf(':');
        final String variable = colon >= 0 ? emptyToNull(trimmed.substring(0, colon).trim()) : null;
        final String type = colon >= 0
                ? firstType(trimmed.substring(colon + 1))
                : (trimmed.contains("*") ? null : firstType(trimmed));

        final Edge.Direction direction;
        if (text.contains("->")) {
            direction = Edge.Direction.LEFT_TO_RIGHT;
        } else if (text.contains("<-")) {
            direction = Edge.Direction.RIGHT_TO_LEFT;
        } else {
            direction = Edge.Direction.UNDIRECTED;
        }
        return new Edge(variable, type, direction);
    }

    private static String firstType(final String typeSegment) {
        final int separator = indexOfAny(typeSegment, '|', '&', ':', '*', '{', ' ', '\t', '\n', '\r');
        final String type = (separator >= 0 ? typeSegment.substring(0, separator) : typeSegment).trim();
        return type.isEmpty() ? null : type;
    }

    private static int indexOfAny(final String value, final char... needles) {
        int best = -1;
        for (final char needle : needles) {
            final int idx = value.indexOf(needle);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static String emptyToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
