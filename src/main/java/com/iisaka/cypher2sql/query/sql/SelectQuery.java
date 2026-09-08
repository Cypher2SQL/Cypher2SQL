package com.iisaka.cypher2sql.query.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SelectQuery implements Query<Grammar> {
    private final List<String> selectColumns = new ArrayList<>();
    private String fromTable;
    private String fromAlias;
    private final List<JoinClause> joins = new ArrayList<>();
    private final List<String> whereClauses = new ArrayList<>();
    private final List<String> orderByColumns = new ArrayList<>();
    private Long limit;
    private Long offset;

    public static SelectQuery from(final String table, final String alias) {
        final SelectQuery select = new SelectQuery();
        select.fromTable = Objects.requireNonNull(table, "table");
        select.fromAlias = Objects.requireNonNull(alias, "alias");
        return select;
    }

    public static SelectQuery selectAllFrom(final String table, final String alias) {
        final SelectQuery select = from(table, alias);
        select.selectColumns.add(alias + ".*");
        return select;
    }

    public SelectQuery addSelectColumn(final String column) {
        selectColumns.add(Objects.requireNonNull(column, "column"));
        return this;
    }

    public SelectQuery addJoin(final JoinClause join) {
        joins.add(Objects.requireNonNull(join, "join"));
        return this;
    }

    public SelectQuery addWhere(final String clause) {
        whereClauses.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    public SelectQuery addOrderBy(final String clause) {
        orderByColumns.add(Objects.requireNonNull(clause, "clause"));
        return this;
    }

    public SelectQuery setLimit(final long limit) {
        this.limit = limit;
        return this;
    }

    public SelectQuery setOffset(final long offset) {
        this.offset = offset;
        return this;
    }

    @Override
    public String render(final Grammar grammar) {
        final String selectClause = "SELECT " + String.join(", ", selectColumns);
        final String fromClause = "FROM " + grammar.quoteIdentifier(fromTable) + " " + fromAlias;
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
