"""SQL model: query builders that render themselves to text for a given :class:`Grammar`.

Mirrors Java's ``com.iisaka.cypher2sql.query.sql`` package.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Protocol, Self


class Grammar(Protocol):
    """A target SQL dialect's identifier-quoting and naming rules, passed to :meth:`Renderable.render`."""

    def name(self) -> str:  # pragma: no cover - protocol
        """The dialect's name, e.g. ``"standard"``."""
        ...

    def quote_identifier(self, identifier: str) -> str:  # pragma: no cover - protocol
        """Quotes a (possibly dotted, e.g. ``table.column``) identifier for this dialect."""
        ...


class Renderable(Protocol):
    """A SQL statement that can render itself for a given :class:`Grammar`."""

    def render(self, grammar: Grammar) -> str:  # pragma: no cover - protocol
        """Renders this query as SQL text using the given dialect's quoting rules."""
        ...


class JoinType(Enum):
    """``INNER`` for a ``MATCH``, ``LEFT`` for an ``OPTIONAL MATCH``."""

    INNER = "INNER"
    LEFT = "LEFT"


@dataclass(frozen=True)
class JoinClause:
    """One ``JOIN`` in a :class:`SelectQuery`.

    Attributes:
        join_type: whether this is an ``INNER`` or ``LEFT`` join.
        table: the joined table's unqualified name.
        alias: the alias assigned to the joined table.
        on_condition: the already-rendered ``ON`` condition SQL.
    """

    join_type: JoinType
    table: str
    alias: str
    on_condition: str


@dataclass
class SelectQuery(Renderable):
    """A mutable ``SELECT`` statement builder, assembled incrementally by
    :meth:`~cypher2sql.read_query.ReadQuery.as_sql` and then rendered to text by :meth:`render`.
    """

    select_columns: list[str] = field(default_factory=list)
    distinct: bool = False
    from_table: str | None = None
    from_subquery: SelectQuery | None = None
    from_alias: str | None = None
    joins: list[JoinClause] = field(default_factory=list)
    where_clauses: list[str] = field(default_factory=list)
    order_by_columns: list[str] = field(default_factory=list)
    limit: int | None = None
    offset: int | None = None

    @classmethod
    def select_from(cls, table: str, alias: str) -> Self:
        """Starts a ``SELECT ... FROM table alias`` with no columns yet."""
        select = cls()
        select.from_table = table
        select.from_alias = alias
        return select

    @classmethod
    def select_all_from(cls, table: str, alias: str) -> Self:
        """Starts a ``SELECT alias.* FROM table alias``."""
        select = cls.select_from(table, alias)
        select.select_columns.append(f"{alias}.*")
        return select

    @classmethod
    def from_subquery_select(cls, subquery: SelectQuery, alias: str) -> Self:
        """Starts a ``SELECT ... FROM (subquery) alias`` with no columns yet."""
        select = cls()
        select.from_subquery = subquery
        select.from_alias = alias
        return select

    def add_select_column(self, column: str) -> Self:
        """Adds a column (or already-rendered expression) to the select list."""
        self.select_columns.append(column)
        return self

    def set_distinct(self) -> Self:
        """Marks this query as ``SELECT DISTINCT``."""
        self.distinct = True
        return self

    def add_join(self, join: JoinClause) -> Self:
        """Adds a join, in the order it should appear in the rendered SQL."""
        self.joins.append(join)
        return self

    def and_last_join_condition(self, extra_condition: str) -> Self:
        """Conjoins an extra condition onto the most recently added join's ``ON`` clause; used to fold an
        ``OPTIONAL MATCH``'s own ``WHERE`` predicate into the outer join condition.

        Raises:
            ValueError: if no join has been added yet.
        """
        if not self.joins:
            raise ValueError("No join to amend.")
        last = self.joins[-1]
        self.joins[-1] = JoinClause(last.join_type, last.table, last.alias, f"{last.on_condition} AND ({extra_condition})")
        return self

    def add_where(self, clause: str) -> Self:
        """Adds a (conjoined) ``WHERE`` clause."""
        self.where_clauses.append(clause)
        return self

    def add_order_by(self, clause: str) -> Self:
        """Adds an ``ORDER BY`` key, already rendered including its ``ASC``/``DESC`` suffix."""
        self.order_by_columns.append(clause)
        return self

    def set_limit(self, limit: int) -> Self:
        """Sets the ``LIMIT`` count."""
        self.limit = limit
        return self

    def set_offset(self, offset: int) -> Self:
        """Sets the ``OFFSET`` (Cypher ``SKIP``) count."""
        self.offset = offset
        return self

    def render(self, grammar: Grammar) -> str:
        """Renders this builder's current state as SQL text, in
        ``SELECT``/``FROM``/``JOIN``/``WHERE``/``ORDER BY``/``LIMIT`` order.
        """
        select_clause = ("SELECT DISTINCT " if self.distinct else "SELECT ") + ", ".join(self.select_columns)
        from_clause = (
            f"FROM ({self.from_subquery.render(grammar)}) {self.from_alias}"
            if self.from_subquery is not None
            else f"FROM {grammar.quote_identifier(self.from_table)} {self.from_alias}"
        )
        join_clause = " ".join(
            f"{join.join_type.value} JOIN {grammar.quote_identifier(join.table)} {join.alias} ON {join.on_condition}"
            for join in self.joins
        )
        where_clause = "" if not self.where_clauses else "WHERE " + " AND ".join(self.where_clauses)
        order_by_clause = "" if not self.order_by_columns else "ORDER BY " + ", ".join(self.order_by_columns)
        limit_offset_clause = self._render_limit_offset()
        return " ".join(
            part for part in (select_clause, from_clause, join_clause, where_clause, order_by_clause, limit_offset_clause) if part
        ).strip()

    def _render_limit_offset(self) -> str:
        if self.limit is None and self.offset is None:
            return ""
        clause = f"LIMIT {self.limit if self.limit is not None else -1}"
        if self.offset is not None:
            clause += f" OFFSET {self.offset}"
        return clause


@dataclass
class InsertQuery(Renderable):
    """An ``INSERT`` statement builder. :meth:`render` always raises ``NotImplementedError``, since this
    project is read-only; these builders exist only as placeholders for a future write-mode enhancement.
    """

    table: str
    values: dict[str, str] = field(default_factory=dict)

    @classmethod
    def into(cls, table: str) -> Self:
        """Starts an ``INSERT INTO table``."""
        return cls(table=table)

    def value(self, column: str, expression: str) -> Self:
        """Sets a column's value expression."""
        self.values[column] = expression
        return self

    def is_empty(self) -> bool:
        """Whether no column values have been set yet."""
        return not self.values

    def render(self, grammar: Grammar) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(_write_disabled_message(type(self).__name__))


@dataclass
class UpdateQuery(Renderable):
    """An ``UPDATE`` statement builder. :meth:`render` always raises ``NotImplementedError``; see
    :class:`InsertQuery`.
    """

    table: str
    assignments: dict[str, str] = field(default_factory=dict)
    where_clauses: list[str] = field(default_factory=list)

    @classmethod
    def table_name(cls, table: str) -> Self:
        """Starts an ``UPDATE table``."""
        return cls(table=table)

    def set(self, column: str, expression: str) -> Self:
        """Adds a ``SET column = expression`` assignment."""
        self.assignments[column] = expression
        return self

    def where(self, clause: str) -> Self:
        """Adds a (conjoined) ``WHERE`` clause."""
        self.where_clauses.append(clause)
        return self

    def has_assignments(self) -> bool:
        """Whether any column assignment has been added."""
        return bool(self.assignments)

    def has_where_clause(self) -> bool:
        """Whether any ``WHERE`` clause has been added."""
        return bool(self.where_clauses)

    def render(self, grammar: Grammar) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(_write_disabled_message(type(self).__name__))


@dataclass
class DeleteQuery(Renderable):
    """A ``DELETE`` statement builder. :meth:`render` always raises ``NotImplementedError``; see
    :class:`InsertQuery`.
    """

    table: str
    where_clauses: list[str] = field(default_factory=list)

    @classmethod
    def from_table(cls, table: str) -> Self:
        """Starts a ``DELETE FROM table``."""
        return cls(table=table)

    def where(self, clause: str) -> Self:
        """Adds a (conjoined) ``WHERE`` clause."""
        self.where_clauses.append(clause)
        return self

    def has_where_clause(self) -> bool:
        """Whether any ``WHERE`` clause has been added."""
        return bool(self.where_clauses)

    def render(self, grammar: Grammar) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(_write_disabled_message(type(self).__name__))


class StandardGrammar:
    """The default :class:`Grammar`: ANSI SQL double-quoted identifiers, with ``"`` escaped by doubling."""

    def name(self) -> str:
        return "standard"

    def quote_identifier(self, identifier: str) -> str:
        """Raises:
        ValueError: if ``identifier`` is ``None``.
        """
        if identifier is None:
            raise ValueError("identifier cannot be None")
        return ".".join(f'"{part.replace("\"", "\"\"")}"' for part in identifier.split("."))


def _write_disabled_message(query_class_name: str) -> str:
    return f"Write queries are disabled in read-only mode. {query_class_name} is reserved for future enhancement."
