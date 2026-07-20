package com.iisaka.cypher2sql.query.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SelectQuery implements Query<Dialect> {
    private final List<String> selectColumns = new ArrayList<>();
    private String fromTable;
    private String fromAlias;
    private final List<JoinClause> joins = new ArrayList<>();
    private final List<String> whereClauses = new ArrayList<>();

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

    @Override
    public String render(final Dialect dialect) {
        final String selectClause = "SELECT " + String.join(", ", selectColumns);
        final String fromClause = "FROM " + dialect.quoteIdentifier(fromTable) + " " + fromAlias;
        final String joinClause = joins.stream()
                .map(join -> join.joinType().name() + " JOIN "
                        + dialect.quoteIdentifier(join.table()) + " " + join.alias()
                        + " ON " + join.onCondition())
                .collect(Collectors.joining(" "));
        final String whereClause = whereClauses.isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", whereClauses);
        return java.util.stream.Stream.of(selectClause, fromClause, joinClause, whereClause)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
    }
}
