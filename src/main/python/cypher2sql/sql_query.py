from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Protocol, Self


class Grammar(Protocol):
    def name(self) -> str:  # pragma: no cover - protocol
        ...

    def quote_identifier(self, identifier: str) -> str:  # pragma: no cover - protocol
        ...


class Renderable(Protocol):
    def render(self, grammar: Grammar) -> str:  # pragma: no cover - protocol
        ...


class JoinType(Enum):
    INNER = "INNER"
    LEFT = "LEFT"


@dataclass(frozen=True)
class JoinClause:
    join_type: JoinType
    table: str
    alias: str
    on_condition: str


@dataclass
class SelectQuery(Renderable):
    select_columns: list[str] = field(default_factory=list)
    from_table: str | None = None
    from_alias: str | None = None
    joins: list[JoinClause] = field(default_factory=list)
    where_clauses: list[str] = field(default_factory=list)
    order_by_columns: list[str] = field(default_factory=list)
    limit: int | None = None
    offset: int | None = None

    @classmethod
    def select_from(cls, table: str, alias: str) -> Self:
        select = cls()
        select.from_table = table
        select.from_alias = alias
        return select

    @classmethod
    def select_all_from(cls, table: str, alias: str) -> Self:
        select = cls.select_from(table, alias)
        select.select_columns.append(f"{alias}.*")
        return select

    def add_select_column(self, column: str) -> Self:
        self.select_columns.append(column)
        return self

    def add_join(self, join: JoinClause) -> Self:
        self.joins.append(join)
        return self

    def add_where(self, clause: str) -> Self:
        self.where_clauses.append(clause)
        return self

    def add_order_by(self, clause: str) -> Self:
        self.order_by_columns.append(clause)
        return self

    def set_limit(self, limit: int) -> Self:
        self.limit = limit
        return self

    def set_offset(self, offset: int) -> Self:
        self.offset = offset
        return self

    def render(self, grammar: Grammar) -> str:
        select_clause = "SELECT " + ", ".join(self.select_columns)
        from_clause = f"FROM {grammar.quote_identifier(self.from_table)} {self.from_alias}"
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
    table: str
    values: dict[str, str] = field(default_factory=dict)

    @classmethod
    def into(cls, table: str) -> Self:
        return cls(table=table)

    def value(self, column: str, expression: str) -> Self:
        self.values[column] = expression
        return self

    def is_empty(self) -> bool:
        return not self.values

    def render(self, grammar: Grammar) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(_write_disabled_message(type(self).__name__))


@dataclass
class UpdateQuery(Renderable):
    table: str
    assignments: dict[str, str] = field(default_factory=dict)
    where_clauses: list[str] = field(default_factory=list)

    @classmethod
    def table_name(cls, table: str) -> Self:
        return cls(table=table)

    def set(self, column: str, expression: str) -> Self:
        self.assignments[column] = expression
        return self

    def where(self, clause: str) -> Self:
        self.where_clauses.append(clause)
        return self

    def has_assignments(self) -> bool:
        return bool(self.assignments)

    def has_where_clause(self) -> bool:
        return bool(self.where_clauses)

    def render(self, grammar: Grammar) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(_write_disabled_message(type(self).__name__))


@dataclass
class DeleteQuery(Renderable):
    table: str
    where_clauses: list[str] = field(default_factory=list)

    @classmethod
    def from_table(cls, table: str) -> Self:
        return cls(table=table)

    def where(self, clause: str) -> Self:
        self.where_clauses.append(clause)
        return self

    def has_where_clause(self) -> bool:
        return bool(self.where_clauses)

    def render(self, grammar: Grammar) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(_write_disabled_message(type(self).__name__))


class StandardGrammar:
    def name(self) -> str:
        return "standard"

    def quote_identifier(self, identifier: str) -> str:
        if identifier is None:
            raise ValueError("identifier cannot be None")
        return ".".join(f'"{part.replace("\"", "\"\"")}"' for part in identifier.split("."))


def _write_disabled_message(query_class_name: str) -> str:
    return f"Write queries are disabled in read-only mode. {query_class_name} is reserved for future enhancement."
