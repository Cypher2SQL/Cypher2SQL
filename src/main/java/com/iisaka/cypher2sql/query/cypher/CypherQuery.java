package com.iisaka.cypher2sql.query.cypher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

public final class CypherQuery {
    private final String raw;
    private final List<CypherPattern> patterns;
    private final List<CypherReturnItem> returnItems;
    private final ParseTree parseTree;

    private CypherQuery(
            final String raw,
            final List<CypherPattern> patterns,
            final List<CypherReturnItem> returnItems,
            final ParseTree parseTree) {
        this.raw = raw;
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.returnItems = Collections.unmodifiableList(new ArrayList<>(returnItems));
        this.parseTree = parseTree;
    }

    public String raw() {
        return raw;
    }

    public List<CypherPattern> patterns() {
        return patterns;
    }

    public List<CypherReturnItem> returnItems() {
        return returnItems;
    }

    public ParseTree parseTree() {
        return parseTree;
    }

    public static CypherQuery parse(final String cypher) {
        final CypherParseTree parseResult = CypherSyntax.cypher25().parse(cypher);
        final ParseTree parseTree = parseResult.parseTree();
        final List<CypherPattern> parsed = new ArrayList<>(CypherPattern.extract(parseResult.parser(), parseTree));
        final List<CypherReturnItem> returnItems = new ArrayList<>(CypherReturnItem.extract(parseResult.parser(), parseTree));
        return new CypherQuery(cypher, parsed, returnItems, parseTree);
    }
}
