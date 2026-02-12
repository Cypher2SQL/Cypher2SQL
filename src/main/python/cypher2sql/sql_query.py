from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Protocol


class StorageModel(Protocol):
    def name(self) -> str:  # pragma: no cover - protocol
        ...


class SqlDialect(StorageModel, Protocol):
    def quote_identifier(self, identifier: str) -> str:  # pragma: no cover - protocol
        ...


class SQLQuery(Protocol):
    def render(self, dialect: SqlDialect) -> str:  # pragma: no cover - protocol
        ...


class JoinType(Enum):
    INNER = "INNER"
    LEFT = "LEFT"


@dataclass(frozen=True)
class SqlJoin:
    join_type: JoinType
    table: str
    alias: str
    on_condition: str


@dataclass
class SqlSelect(SQLQuery):
    select_columns: List[str] = field(default_factory=list)
    from_table: str | None = None
    from_alias: str | None = None
    joins: List[SqlJoin] = field(default_factory=list)
    where_clauses: List[str] = field(default_factory=list)

    @classmethod
    def select_all_from(cls, table: str, alias: str) -> "SqlSelect":
        select = cls()
        select.select_columns.append(f"{alias}.*")
        select.from_table = table
        select.from_alias = alias
        return select

    def add_join(self, join: SqlJoin) -> "SqlSelect":
        self.joins.append(join)
        return self

    def add_where(self, clause: str) -> "SqlSelect":
        self.where_clauses.append(clause)
        return self

    def render(self, dialect: SqlDialect) -> str:
        select_clause = "SELECT " + ", ".join(self.select_columns)
        from_clause = f"FROM {dialect.quote_identifier(self.from_table)} {self.from_alias}"
        join_clause = " ".join(
            f"{join.join_type.value} JOIN {dialect.quote_identifier(join.table)} {join.alias} ON {join.on_condition}"
            for join in self.joins
        )
        where_clause = "" if not self.where_clauses else " WHERE " + " AND ".join(self.where_clauses)
        return " ".join(part for part in (select_clause, from_clause, join_clause, where_clause) if part).strip()


@dataclass
class SqlInsert(SQLQuery):
    table: str
    values: Dict[str, str] = field(default_factory=dict)

    @classmethod
    def into(cls, table: str) -> "SqlInsert":
        return cls(table=table)

    def value(self, column: str, expression: str) -> "SqlInsert":
        self.values[column] = expression
        return self

    def is_empty(self) -> bool:
        return not self.values

    def render(self, dialect: SqlDialect) -> str:
        # Placeholder only: write queries are intentionally disabled while the project is read-only.
        raise NotImplementedError(
            "Write queries are disabled in read-only mode. SqlInsert is reserved for future enhancement."
        )


class BasicSqlDialect:
    def name(self) -> str:
        return "basic"

    def quote_identifier(self, identifier: str) -> str:
        return f'"{identifier}"'
