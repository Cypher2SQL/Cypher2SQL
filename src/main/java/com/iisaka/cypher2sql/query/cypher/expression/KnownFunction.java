package com.iisaka.cypher2sql.query.cypher.expression;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Cypher functions this project can translate to SQL, mapping each one's Cypher name to its SQL
 * equivalent and whether it is an aggregate.
 */
public enum KnownFunction {
    COUNT("count", "COUNT", true),
    SUM("sum", "SUM", true),
    AVG("avg", "AVG", true),
    MIN("min", "MIN", true),
    MAX("max", "MAX", true),
    COALESCE("coalesce", "COALESCE", false),
    ABS("abs", "ABS", false),
    CEIL("ceil", "CEIL", false),
    FLOOR("floor", "FLOOR", false),
    ROUND("round", "ROUND", false),
    SQRT("sqrt", "SQRT", false),
    LOG("log", "LOG", false),
    LOG10("log10", "LOG10", false),
    EXP("exp", "EXP", false),
    SIN("sin", "SIN", false),
    COS("cos", "COS", false),
    TAN("tan", "TAN", false),
    TRIM("trim", "TRIM", false),
    LTRIM("ltrim", "LTRIM", false),
    RTRIM("rtrim", "RTRIM", false),
    SUBSTRING("substring", "SUBSTRING", false),
    REPLACE("replace", "REPLACE", false),
    LEFT("left", "LEFT", false),
    RIGHT("right", "RIGHT", false),
    TOUPPER("toupper", "UPPER", false),
    TOLOWER("tolower", "LOWER", false);

    private static final Map<String, KnownFunction> BY_CYPHER_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(function -> function.cypherName, Function.identity()));

    private final String cypherName;
    private final String sqlName;
    private final boolean aggregate;

    KnownFunction(final String cypherName, final String sqlName, final boolean aggregate) {
        this.cypherName = cypherName;
        this.sqlName = sqlName;
        this.aggregate = aggregate;
    }

    /** The SQL function name to render this as. */
    public String sqlName() {
        return sqlName;
    }

    /** Whether this function aggregates rows, e.g. {@code count}, {@code sum}. */
    public boolean aggregate() {
        return aggregate;
    }

    /** Looks up a known function by its Cypher name, case-insensitively. */
    public static Optional<KnownFunction> forName(final String cypherName) {
        return Optional.ofNullable(BY_CYPHER_NAME.get(cypherName.toLowerCase()));
    }
}
