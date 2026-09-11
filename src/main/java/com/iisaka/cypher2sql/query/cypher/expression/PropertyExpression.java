package com.iisaka.cypher2sql.query.cypher.expression;

/**
 * A property access, e.g. {@code p.name}.
 *
 * @param receiver the expression the property is read from, typically a {@link VariableExpression}
 * @param property the Cypher property name
 */
public record PropertyExpression(Expression receiver, String property) implements Expression {
    @Override
    public boolean isAggregate() {
        return receiver.isAggregate();
    }
}
