package com.iisaka.cypher2sql.query.cypher.expression;

import org.antlr.v4.runtime.tree.ParseTree;
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

public sealed interface Expression permits VariableExpression,
        PropertyExpression,
        ConstantExpression,
        FunctionExpression,
        BinaryExpression,
        UnaryExpression,
        CaseExpression,
        WildcardExpression {

    boolean isAggregate();

    static Expression parse(final Cypher25Parser.ExpressionContext context) {
        return ExpressionPrecedenceParser.parse(context);
    }

    static IllegalArgumentException unsupported(final ParseTree tree) {
        return new IllegalArgumentException("Unsupported expression: " + tree.getText());
    }
}
