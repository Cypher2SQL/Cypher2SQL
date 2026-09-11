package com.iisaka.cypher2sql.query.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A mutable {@code SELECT} statement builder, assembled incrementally by
 * {@link com.iisaka.cypher2sql.query.read.ReadQuery#asSql()} and then rendered to text by {@link #render}.
 */
public final class SelectQuery implements Query<Grammar> {
    private final List<String> selectColumns = new ArrayList<>();
    private boolean distinct;
    private String fromTable;
    private SelectQuery fromSubquery;
    private String fromAlias;
    private final List<JoinClause> joins = new ArrayList<>();
    private final List<String> whereClauses = new ArrayList<>();
    private final List<String> orderByColumns = new ArrayList<>();
    private Long limit;
    private Long offset;

    /** Starts a {@code SELECT ... FROM table alias} with no columns yet. */
    public static SelectQuery from(final String table, final String alias) {
        final SelectQuery select = new SelectQuery();
        select.fromTable = Objects.requireNonNull(table, "table");
        select.fromAlias = Objects.requireNonNull(alias, "alias");
        return select;
    }

    /** Starts a {@code SELECT ... FROM (subquery) alias} with no columns yet. */
    public static SelectQuery fromSubquery(final SelectQuery subquery, final String alias) {
        final SelectQuery select = new SelectQuery();
        select.fromSubquery = Objects.requireNonNull(subquery, "subquery");
        select.fromAlias = Objects.requireNonNull(alias, "alias");
        return select;
    }

    /** Starts a {@code SELECT alias.* FROM table alias}. */
    public static SelectQuery selectAllFrom(final String table, final String alias) {
        final SelectQuery select = from(table, alias);
        select.selectColumns.add(alias + ".*");
        return select;
    }

    /** Adds a column (or already-rendered expression) to the select list. */
    public SelectQuery addSelectColumn(final String column) {
        selectColumns.add(Objects.requireNonNull(column, "column"));
        return this;
    }

    /** Marks this query as {@code SELECT DISTINCT}. */
    public SelectQuery setDistinct() {
        this.distinct = true;
        return this;
    }

    /** Adds a join, in the order it should appear in the rendered SQL. */
    public SelectQuery addJoin(final JoinClause join) {
        joins.add(Objects.requireNonNull(join, "join"));
        return this;
    }

    /**
     * Conjoins an extra condition onto the most recently added join's {@code ON} clause; used to fold an
     * {@code OPTIONAL MATCH}'s own {@code WHERE} predicate into the outer join condition.
     *
     * @throws IllegalStateException if no join has been added yet
     */
    public SelectQuery andLastJoinCondition(final String extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        if (joins.isEmpty()) {
            throw new IllegalStateException("No join to amend.");
        }
        final int lastIndex = joins.size() - 1;
        final JoinClause last = joins.get(lastIndex);
        joins.set(lastIndex, new JoinClause(
                last.joinType(), last.table(), last.alias(), last.onCondition() + " AND (" + extraCondition + ")"));
        return this;
    }

    /** Adds a (conjoined) {@code WHERE} clause. */
    public SelectQuery addWhere(final String clause) {
        whereClauses.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    /** Adds an {@code ORDER BY} key, already rendered including its {@code ASC}/{@code DESC} suffix. */
    public SelectQuery addOrderBy(final String clause) {
        orderByColumns.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    /** Sets the {@code LIMIT} count. */
    public SelectQuery setLimit(final long limit) {
        this.limit = limit;
        return this;
    }

    /** Sets the {@code OFFSET} (Cypher {@code SKIP}) count. */
    public SelectQuery setOffset(final long offset) {
        this.offset = offset;
        return this;
    }

    /** Renders this builder's current state as SQL text, in {@code SELECT}/{@code FROM}/{@code JOIN}/{@code WHERE}/{@code ORDER BY}/{@code LIMIT} order. */
    @Override
    public String render(final Grammar grammar) {
        final String selectClause = distinct
                ? "SELECT DISTINCT " + String.join(", ", selectColumns)
                : "SELECT " + String.join(", ", selectColumns);
        final String fromClause = fromSubquery == null
                ? "FROM " + grammar.quoteIdentifier(fromTable) + " " + fromAlias
                : "FROM (" + fromSubquery.render(grammar) + ") " + fromAlias;
        final String joinClause = joins.stream()
                .map(join -> join.joinType().name() + " JOIN "
                        + grammar.quoteIdentifier(join.table()) + " " + join.alias()
                        + " ON " + join.onCondition())
                .collect(Collectors.joining(" "));
        final String whereClause = whereClauses.isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", whereClauses);
        final String orderByClause = orderByColumns.isEmpty()
                ? ""
                : "ORDER BY " + String.join(", ", orderByColumns);
        final String limitOffsetClause = renderLimitOffset();
        return java.util.stream.Stream.of(selectClause, fromClause, joinClause, whereClause, orderByClause, limitOffsetClause)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String renderLimitOffset() {
        if (limit == null && offset == null) {
            return "";
        }
        final StringBuilder clause = new StringBuilder();
        clause.append("LIMIT ").append(limit == null ? -1 : limit);
        if (offset != null) {
            clause.append(" OFFSET ").append(offset);
        }
        return clause.toString();
    }
}
