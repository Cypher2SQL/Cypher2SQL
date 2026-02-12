package com.iisaka.cypher2sql.query.cypher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

public final class Query {
    private final String raw;
    private final List<Pattern> patterns;
    private final List<ReturnItem> returnItems;
    private final ParseTree parseTree;

    private Query(
            final String raw,
            final List<Pattern> patterns,
            final List<ReturnItem> returnItems,
            final ParseTree parseTree) {
        this.raw = raw;
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.returnItems = Collections.unmodifiableList(new ArrayList<>(returnItems));
        this.parseTree = parseTree;
    }

    public String raw() {
        return raw;
    }

    public List<Pattern> patterns() {
        return patterns;
    }

    public List<ReturnItem> returnItems() {
        return returnItems;
    }

    public ParseTree parseTree() {
        return parseTree;
    }

    public static Query parse(final String cypher) {
        final Syntax.ParsedCypher parseResult = Syntax.cypher25().parse(cypher);
        final ParseTree parseTree = parseResult.parseTree();
        final List<Pattern> parsed = new ArrayList<>(parseResult.patterns());
        final List<ReturnItem> returnItems = new ArrayList<>(parseResult.returnItems());
        return new Query(cypher, parsed, returnItems, parseTree);
    }
}
