package com.iisaka.cypher2sql.query.cypher.expression;

import org.neo4j.cypher.internal.parser.v25.Cypher25Parser;

import java.util.ArrayList;
import java.util.List;

public record FunctionExpression(String name, List<Expression> arguments) implements Expression {
    @Override
    public boolean isAggregate() {
        return KnownFunction.forName(name).map(KnownFunction::aggregate).orElse(false)
                || arguments.stream().anyMatch(Expression::isAggregate);
    }

    static Expression from(final Cypher25Parser.FunctionInvocationContext context) {
        if (context.DISTINCT() != null) {
            throw Expression.unsupported(context);
        }
        final List<Expression> arguments = new ArrayList<>();
        for (final Cypher25Parser.FunctionArgumentContext argument : context.functionArgument()) {
            arguments.add(Expression.parse(argument.expression()));
        }
        return new FunctionExpression(context.functionName().getText(), List.copyOf(arguments));
    }
}
