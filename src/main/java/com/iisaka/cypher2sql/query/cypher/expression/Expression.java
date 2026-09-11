package com.iisaka.cypher2sql.query.cypher.expression;

import org.antlr.v4.runtime.tree.ParseTree;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

/**
 * A parsed Cypher expression tree node, as found in {@code WHERE}, {@code RETURN}, {@code ORDER BY}, and
 * similar clauses.
 *
 * @see VariableExpression
 * @see PropertyExpression
 * @see ConstantExpression
 * @see FunctionExpression
 * @see BinaryExpression
 * @see UnaryExpression
 * @see CaseExpression
 * @see WildcardExpression
 */
public sealed interface Expression permits VariableExpression,
        PropertyExpression,
        ConstantExpression,
        FunctionExpression,
        BinaryExpression,
        UnaryExpression,
        CaseExpression,
        WildcardExpression {

    /** Whether this expression is, or contains, an aggregate function call such as {@code count(*)}. */
    boolean isAggregate();

    /**
     * Parses an ANTLR expression context into an {@link Expression} tree, respecting Cypher's operator
     * precedence.
     *
     * @throws IllegalArgumentException if the expression uses a construct not yet supported
     */
    static Expression parse(final Cypher25Parser.ExpressionContext context) {
        return ExpressionPrecedenceParser.parse(context);
    }

    /** Builds the standard "unsupported expression" exception for the given parse-tree node's source text. */
    static IllegalArgumentException unsupported(final ParseTree tree) {
        return new IllegalArgumentException("Unsupported expression: " + tree.getText());
    }
}
