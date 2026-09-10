package com.iisaka.cypher2sql.query.cypher;

import com.iisaka.cypher2sql.query.cypher.expression.ConstantExpression;
import com.iisaka.cypher2sql.query.cypher.expression.Expression;
import com.iisaka.cypher2sql.query.read.BoundNode;
import com.iisaka.cypher2sql.query.read.BoundPattern;
import com.iisaka.cypher2sql.query.read.ReadQuery;
import com.iisaka.cypher2sql.query.sql.SelectQuery;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed, read-only Cypher query: an ordered list of {@link Clause}s built from the ANTLR parse tree
 * produced by {@link Syntax}. This is the entry point for translating Cypher text to SQL — parse with
 * {@link #of(String)}, then either inspect the clauses directly or call {@link #asSql(SchemaDefinition)}.
 */
public final class Query {
    private final String raw;
    private final ParseTree parseTree;
    private final List<Clause> clauses;
    private final boolean hasVariableLengthTraversal;

    private Query(
            final String raw,
            final ParseTree parseTree,
            final List<Clause> clauses,
            final boolean hasVariableLengthTraversal) {
        this.raw = raw;
        this.parseTree = parseTree;
        this.clauses = List.copyOf(clauses);
        this.hasVariableLengthTraversal = hasVariableLengthTraversal;
    }

    /** The original Cypher query text this was parsed from. */
    public String raw() {
        return raw;
    }

    /** The raw ANTLR parse tree produced by {@link Syntax}. */
    public ParseTree parseTree() {
        return parseTree;
    }

    /** All clauses in this query, in source order. */
    public List<Clause> clauses() {
        return clauses;
    }

    /** All {@code MATCH}/{@code OPTIONAL MATCH} clauses in this query, in source order. */
    public List<MatchClause> matchClauses() {
        return clauses.stream().filter(MatchClause.class::isInstance).map(MatchClause.class::cast).toList();
    }

    /** The query's {@code WITH} clause, if it has one. Only a single {@code WITH} clause is supported. */
    public Optional<WithClause> withClause() {
        return clauses.stream().filter(WithClause.class::isInstance).map(WithClause.class::cast).findFirst();
    }

    /** Whether this query has a {@code WITH} clause. */
    public boolean hasWithClause() {
        return withClause().isPresent();
    }

    /** The query's {@code RETURN} clause, or an empty clause if the query has none. */
    public ReturnClause returnClause() {
        return clauses.stream().filter(ReturnClause.class::isInstance).map(ReturnClause.class::cast).findFirst()
                .orElseGet(() -> new ReturnClause(List.of(), false, List.of(), null, null, null));
    }

    /** Whether any pattern uses a variable-length traversal (e.g. {@code -[*1..3]->}), which is not yet supported. */
    public boolean hasVariableLengthTraversal() {
        return hasVariableLengthTraversal;
    }

    /**
     * Parses a Cypher query string using the ANTLR-based {@link Syntax#cypher25()} grammar.
     *
     * @throws IllegalArgumentException if the text is not syntactically valid Cypher
     */
    public static Query of(final String cypher) {
        final Syntax.ParsedCypher parsed = Syntax.cypher25().parse(cypher);
        final ParseTree parseTree = parsed.parseTree();
        final String[] ruleNames = parsed.ruleNames();
        return new Query(
                cypher,
                parseTree,
                extractClauses(parseTree, ruleNames),
                hasVariableLengthTraversal(parseTree, ruleNames));
    }

    /**
     * Binds this query against the given schema and renders it as a SQL {@code SELECT}.
     *
     * @throws UnsupportedOperationException if the query uses a feature not yet translatable to SQL
     * @throws IllegalArgumentException      if a pattern's labels or properties cannot be resolved against the schema
     */
    public SelectQuery asSql(final SchemaDefinition schema) {
        return asReadQuery(schema).asSql();
    }

    /**
     * Binds this query's patterns and clauses against the schema, producing the intermediate read-query
     * representation that {@link #asSql} renders to SQL.
     *
     * @throws UnsupportedOperationException if the query uses a feature not yet translatable to SQL
     * @throws IllegalArgumentException      if a pattern's labels or properties cannot be resolved against the schema
     */
    public ReadQuery asReadQuery(final SchemaDefinition schema) {
        if (hasVariableLengthTraversal()) {
            // Placeholder only: recursive traversal translation is intentionally not implemented yet.
            throw new UnsupportedOperationException(
                    "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.");
        }

        final List<MatchClause> matchClauses = matchClauses();
        final List<WithClause> withClauses = clauses.stream()
                .filter(WithClause.class::isInstance).map(WithClause.class::cast).toList();
        if (!withClauses.isEmpty()) {
            if (withClauses.size() > 1) {
                throw new UnsupportedOperationException("Only one WITH clause is supported yet.");
            }
            if (hasMatchAfterWith(clauses)) {
                throw new UnsupportedOperationException("MATCH after WITH is not supported yet.");
            }
            if (withClauses.get(0).hasMixedAggregation()) {
                throw new UnsupportedOperationException(
                        "Aggregation grouping in WITH is not supported yet; all WITH items must be aggregate expressions, or none.");
            }
        }

        final Map<String, BoundNode> boundByVariable = new HashMap<>();
        final int[] nextAliasIndex = {0};
        final List<BoundPattern> patterns = new ArrayList<>();
        for (final MatchClause matchClause : matchClauses) {
            patterns.addAll(matchClause.bind(schema, boundByVariable, nextAliasIndex));
        }

        final Expression baseWhereExpression = matchClauses.isEmpty() ? null : matchClauses.get(0).whereExpression();
        final ReturnClause returnClause = returnClause();
        if (withClauses.isEmpty()) {
            return new ReadQuery(
                    patterns, baseWhereExpression, returnClause.items(), returnClause.distinct(),
                    returnClause.orderItems(), returnClause.skip(), returnClause.limit());
        }
        final WithClause withClause = withClauses.get(0);
        return new ReadQuery(
                patterns, baseWhereExpression, withClause.items(), withClause.distinct(),
                withClause.orderItems(), withClause.skip(), withClause.limit(),
                new ReadQuery.FinalStage(
                        withClause.whereExpression(), returnClause.items(), returnClause.distinct(),
                        returnClause.orderItems(), returnClause.skip(), returnClause.limit()));
    }

    private static boolean hasMatchAfterWith(final List<Clause> clauses) {
        boolean sawWith = false;
        for (final Clause clause : clauses) {
            if (clause instanceof WithClause) {
                sawWith = true;
            } else if (clause instanceof MatchClause && sawWith) {
                return true;
            }
        }
        return false;
    }

    private static List<Clause> extractClauses(final ParseTree parseTree, final String[] ruleNames) {
        final List<Clause> clauses = new ArrayList<>();
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof Cypher25Parser.MatchClauseContext matchClauseContext) {
                clauses.add(toMatchClause(matchClauseContext, ruleNames));
            } else if (current instanceof Cypher25Parser.WithClauseContext withClauseContext) {
                clauses.add(toWithClause(withClauseContext));
            } else if (current instanceof Cypher25Parser.ReturnClauseContext returnClauseContext) {
                clauses.add(toReturnClause(returnClauseContext));
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
        return clauses;
    }

    private static MatchClause toMatchClause(
            final Cypher25Parser.MatchClauseContext matchClauseContext, final String[] ruleNames) {
        final boolean optional = matchClauseContext.OPTIONAL() != null;
        final Expression whereExpression = matchClauseContext.whereClause() == null
                ? null
                : Expression.parse(matchClauseContext.whereClause().expression());
        final List<ParserRuleContext> patternRoots = findPatternElements(matchClauseContext.patternList(), ruleNames);
        final List<Pattern> patterns = new ArrayList<>();
        for (final ParserRuleContext patternRoot : patternRoots) {
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
                continue;
            }

            final List<Node> nodes = new ArrayList<>();
            for (final ParserRuleContext nodeContext : nodeContexts) {
                nodes.add(Node.fromPatternText(nodeContext.getText()));
            }

            final List<Edge> edges = new ArrayList<>();
            for (final ParserRuleContext relationshipContext : relationshipContexts) {
                edges.add(Edge.fromPatternText(relationshipContext.getText()));
            }
            patterns.add(new Pattern(nodes, edges));
        }
        return new MatchClause(patterns, optional, whereExpression, matchClauseContext);
    }

    private static WithClause toWithClause(final Cypher25Parser.WithClauseContext withClauseContext) {
        final Cypher25Parser.ReturnBodyContext returnBody = withClauseContext.returnBody();
        final Expression whereExpression = withClauseContext.whereClause() == null
                ? null
                : Expression.parse(withClauseContext.whereClause().expression());
        return new WithClause(
                extractProjectionItems(returnBody),
                extractDistinct(returnBody),
                extractOrderItems(returnBody),
                extractSkip(returnBody),
                extractLimit(returnBody),
                whereExpression,
                withClauseContext);
    }

    private static ReturnClause toReturnClause(final Cypher25Parser.ReturnClauseContext returnClauseContext) {
        final Cypher25Parser.ReturnBodyContext returnBody = returnClauseContext.returnBody();
        return new ReturnClause(
                extractProjectionItems(returnBody),
                extractDistinct(returnBody),
                extractOrderItems(returnBody),
                extractSkip(returnBody),
                extractLimit(returnBody),
                returnClauseContext);
    }

    private static List<ParserRuleContext> findPatternElements(final ParseTree parseTree, final String[] ruleNames) {
        final List<ParserRuleContext> roots = new ArrayList<>();
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleNames[context.getRuleIndex()];
                if ("patternElement".equals(ruleName)) {
                    roots.add(context);
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
        return roots;
    }

    private static List<ProjectionItem> extractProjectionItems(final Cypher25Parser.ReturnBodyContext returnBody) {
        if (returnBody == null || returnBody.returnItems() == null) {
            return List.of();
        }
        return projectionItems(returnBody.returnItems());
    }

    private static boolean extractDistinct(final Cypher25Parser.ReturnBodyContext returnBody) {
        return returnBody != null && returnBody.DISTINCT() != null;
    }

    private static List<OrderItem> extractOrderItems(final Cypher25Parser.ReturnBodyContext returnBody) {
        if (returnBody == null || returnBody.orderBy() == null) {
            return List.of();
        }
        final List<OrderItem> orderItems = new ArrayList<>();
        for (final Cypher25Parser.OrderItemContext orderItem : returnBody.orderBy().orderItem()) {
            orderItems.add(new OrderItem(Expression.parse(orderItem.expression()), orderItem.descToken() != null));
        }
        return orderItems;
    }

    private static Long extractSkip(final Cypher25Parser.ReturnBodyContext returnBody) {
        if (returnBody == null || returnBody.skip() == null) {
            return null;
        }
        return requireIntegerLiteral(Expression.parse(returnBody.skip().expression()), "SKIP");
    }

    private static Long extractLimit(final Cypher25Parser.ReturnBodyContext returnBody) {
        if (returnBody == null || returnBody.limit() == null) {
            return null;
        }
        return requireIntegerLiteral(Expression.parse(returnBody.limit().expression()), "LIMIT");
    }

    private static Long requireIntegerLiteral(final Expression expression, final String clause) {
        if (expression instanceof ConstantExpression constant && constant.value() instanceof Long value) {
            return value;
        }
        throw new UnsupportedOperationException(
                clause + " must be an integer literal; parameters are not supported yet.");
    }

    private static List<ProjectionItem> projectionItems(final Cypher25Parser.ReturnItemsContext returnItems) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(returnItems);
        final List<Cypher25Parser.ReturnItemContext> returnItemContexts = new ArrayList<>();
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof Cypher25Parser.ReturnItemContext context) {
                returnItemContexts.add(context);
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }

        final List<ProjectionItem> items = new ArrayList<>();
        for (final Cypher25Parser.ReturnItemContext context : returnItemContexts) {
            items.add(ProjectionItem.fromExpression(
                    Expression.parse(context.expression()),
                    context.variable() == null ? null : context.variable().getText()));
        }
        return items;
    }

    private static boolean hasVariableLengthTraversal(final ParseTree parseTree, final String[] ruleNames) {
        final Deque<ParseTree> stack = new ArrayDeque<>();
        stack.push(parseTree);
        while (!stack.isEmpty()) {
            final ParseTree current = stack.pop();
            if (current instanceof ParserRuleContext context) {
                final String ruleName = ruleNames[context.getRuleIndex()];
                if ("relationshipPattern".equals(ruleName) && context.getText().contains("*")) {
                    return true;
                }
            }
            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }
        return false;
    }

}
