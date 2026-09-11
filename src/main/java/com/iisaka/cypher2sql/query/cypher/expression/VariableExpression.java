package com.iisaka.cypher2sql.query.cypher.expression;

import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

/**
 * A reference to a bound pattern variable, e.g. {@code p}.
 *
 * @param name the variable name
 */
public record VariableExpression(String name) implements Expression {
    @Override
    public boolean isAggregate() {
        return false;
    }

    static VariableExpression from(final Cypher25Parser.VariableContext context) {
        return new VariableExpression(context.getText());
    }
}
