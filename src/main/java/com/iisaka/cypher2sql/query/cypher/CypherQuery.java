package com.iisaka.cypher2sql.query.cypher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

public final class CypherQuery {
    private final String raw;
    private final List<CypherPattern> patterns;
    private final ParseTree parseTree;

    private CypherQuery(final String raw, final List<CypherPattern> patterns, final ParseTree parseTree) {
        this.raw = raw;
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.parseTree = parseTree;
    }

    public String raw() {
        return raw;
    }

    public List<CypherPattern> patterns() {
        return patterns;
    }

    public ParseTree parseTree() {
        return parseTree;
    }

    public static CypherQuery parse(final String cypher) {
        final CypherParseResult parseResult = CypherSyntax.cypher25().parse(cypher);
        final ParseTree parseTree = parseResult.parseTree();
        final List<CypherPattern> parsed = new ArrayList<>(CypherPatternExtractor.extract(parseResult.parser(), parseTree));
        return new CypherQuery(cypher, parsed, parseTree);
    }
}
