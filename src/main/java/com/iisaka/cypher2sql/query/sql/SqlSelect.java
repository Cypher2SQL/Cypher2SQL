package com.iisaka.cypher2sql.query.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class SqlSelect implements SQLQuery<SqlDialect> {
    private final List<String> selectColumns = new ArrayList<>();
    private String fromTable;
    private String fromAlias;
    private final List<SqlJoin> joins = new ArrayList<>();
    private final List<String> whereClauses = new ArrayList<>();

    public static SqlSelect selectAllFrom(final String table, final String alias) {
        final SqlSelect select = new SqlSelect();
        select.selectColumns.add(alias + ".*");
        select.fromTable = table;
        select.fromAlias = alias;
        return select;
    }

    public SqlSelect addJoin(final SqlJoin join) {
        joins.add(join);
        return this;
    }

    public SqlSelect addWhere(final String clause) {
        whereClauses.add(clause);
        return this;
    }

    @Override
    public String render(final SqlDialect dialect) {
        final String selectClause = "SELECT " + String.join(", ", selectColumns);
        final String fromClause = "FROM " + dialect.quoteIdentifier(fromTable) + " " + fromAlias;
        final String joinClause = joins.stream()
                .map(join -> join.joinType().name() + " JOIN "
                        + dialect.quoteIdentifier(join.table()) + " " + join.alias()
                        + " ON " + join.onCondition())
                .collect(Collectors.joining(" "));
        final String whereClause = whereClauses.isEmpty()
                ? ""
                : " WHERE " + String.join(" AND ", whereClauses);
        return String.join(" ", List.of(selectClause, fromClause, joinClause, whereClause)).trim();
    }
}
