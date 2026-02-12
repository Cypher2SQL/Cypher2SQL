from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Protocol


class Dialect(Protocol):
    def name(self) -> str:  # pragma: no cover - protocol
        ...

    def quote_identifier(self, identifier: str) -> str:  # pragma: no cover - protocol
        ...


class SqlRenderable(Protocol):
    def render(self, dialect: Dialect) -> str:  # pragma: no cover - protocol
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
class SelectQuery(SqlRenderable):
    select_columns: List[str] = field(default_factory=list)
    from_table: str | None = None
    from_alias: str | None = None
    joins: List[JoinClause] = field(default_factory=list)
    where_clauses: List[str] = field(default_factory=list)

    @classmethod
    def select_from(cls, table: str, alias: str) -> "SelectQuery":
        select = cls()
        select.from_table = table
        select.from_alias = alias
        return select

    @classmethod
    def select_all_from(cls, table: str, alias: str) -> "SelectQuery":
        select = cls.select_from(table, alias)
        select.select_columns.append(f"{alias}.*")
        return select

    def add_select_column(self, column: str) -> "SelectQuery":
        self.select_columns.append(column)
        return self

    def add_join(self, join: JoinClause) -> "SelectQuery":
        self.joins.append(join)
        return self

    def add_where(self, clause: str) -> "SelectQuery":
        self.where_clauses.append(clause)
        return self

    def render(self, dialect: Dialect) -> str:
        select_clause = "SELECT " + ", ".join(self.select_columns)
        from_clause = f"FROM {dialect.quote_identifier(self.from_table)} {self.from_alias}"
        join_clause = " ".join(
            f"{join.join_type.value} JOIN {dialect.quote_identifier(join.table)} {join.alias} ON {join.on_condition}"
            for join in self.joins
        )
        where_clause = "" if not self.where_clauses else " WHERE " + " AND ".join(self.where_clauses)
        return " ".join(part for part in (select_clause, from_clause, join_clause, where_clause) if part).strip()


@dataclass
class InsertQuery(SqlRenderable):
    table: str
    values: Dict[str, str] = field(default_factory=dict)

    @classmethod
    def into(cls, table: str) -> "InsertQuery":
        return cls(table=table)

    def value(self, column: str, expression: str) -> "InsertQuery":
        self.values[column] = expression
        return self

    def is_empty(self) -> bool:
        return not self.values

    def render(self, dialect: Dialect) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(
            "Write queries are disabled in read-only mode. InsertQuery is reserved for future enhancement."
        )


@dataclass
class UpdateQuery(SqlRenderable):
    table: str
    assignments: Dict[str, str] = field(default_factory=dict)
    where_clauses: List[str] = field(default_factory=list)

    @classmethod
    def table_name(cls, table: str) -> "UpdateQuery":
        return cls(table=table)

    def set(self, column: str, expression: str) -> "UpdateQuery":
        self.assignments[column] = expression
        return self

    def where(self, clause: str) -> "UpdateQuery":
        self.where_clauses.append(clause)
        return self

    def has_assignments(self) -> bool:
        return bool(self.assignments)

    def has_where_clause(self) -> bool:
        return bool(self.where_clauses)

    def render(self, dialect: Dialect) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(
            "Write queries are disabled in read-only mode. UpdateQuery is reserved for future enhancement."
        )


@dataclass
class DeleteQuery(SqlRenderable):
    table: str
    where_clauses: List[str] = field(default_factory=list)

    @classmethod
    def from_table(cls, table: str) -> "DeleteQuery":
        return cls(table=table)

    def where(self, clause: str) -> "DeleteQuery":
        self.where_clauses.append(clause)
        return self

    def has_where_clause(self) -> bool:
        return bool(self.where_clauses)

    def render(self, dialect: Dialect) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(
            "Write queries are disabled in read-only mode. DeleteQuery is reserved for future enhancement."
        )


class BasicDialect:
    def name(self) -> str:
        return "basic"

    def quote_identifier(self, identifier: str) -> str:
        return f'"{identifier}"'
