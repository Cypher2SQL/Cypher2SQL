from __future__ import annotations

from dataclasses import dataclass

from .cypher_query import Query
from .read_query import BoundNode, BoundPattern, FinalStage, ReadQuery
from .schema import SchemaDefinition
from .sql_query import SelectQuery


@dataclass
class Mapping:
    """Binds a parsed :class:`~cypher2sql.cypher_query.Query` against a schema and renders it to SQL.

    This is the Python entry point for translation, mirroring Java's
    ``com.iisaka.cypher2sql.query.cypher.Query.asSql``/``asReadQuery``.
    """

    schema: SchemaDefinition

    def to_sql(self, query: Query) -> SelectQuery:
        """Binds ``query`` against :attr:`schema` and renders it as a SQL ``SELECT``.

        Raises:
            NotImplementedError: if the query uses a feature not yet translatable to SQL.
            ValueError: if a pattern's labels or properties cannot be resolved against the schema.
        """
        return self.to_read_query(query).as_sql()

    def to_read_query(self, query: Query) -> ReadQuery:
        """Binds ``query``'s patterns and clauses against :attr:`schema`, producing the intermediate
        read-query representation that :meth:`to_sql` renders to SQL.

        Raises:
            NotImplementedError: if the query uses a feature not yet translatable to SQL.
            ValueError: if a pattern's labels or properties cannot be resolved against the schema.
        """
        if query.has_variable_length_traversal:
            return self._translate_variable_length_traversal(query)
        if query.has_with_clause:
            if query.has_multiple_with_clauses:
                raise NotImplementedError("Only one WITH clause is supported yet.")
            if query.has_match_after_with:
                raise NotImplementedError("MATCH after WITH is not supported yet.")
            if query.with_clause.has_mixed_aggregation():
                raise NotImplementedError(
                    "Aggregation grouping in WITH is not supported yet; all WITH items must be aggregate expressions, or none."
                )

        bound_by_variable: dict[str, BoundNode] = {}
        next_alias_index = [0]
        bound_patterns: list[BoundPattern] = []
        match_clauses = query.match_clauses
        for match_clause in match_clauses:
            bound_patterns.extend(match_clause.bind(self.schema, bound_by_variable, next_alias_index))

        base_where_expression = match_clauses[0].where_expression if match_clauses else None
        return_clause = query.return_clause
        if not query.has_with_clause:
            return ReadQuery(
                bound_patterns,
                base_where_expression,
                return_clause.items,
                return_clause.distinct,
                return_clause.order_items,
                return_clause.skip,
                return_clause.limit,
            )
        with_clause = query.with_clause
        return ReadQuery(
            bound_patterns,
            base_where_expression,
            with_clause.items,
            with_clause.distinct,
            with_clause.order_items,
            with_clause.skip,
            with_clause.limit,
            FinalStage(
                with_clause.where_expression,
                return_clause.items,
                return_clause.distinct,
                return_clause.order_items,
                return_clause.skip,
                return_clause.limit,
            ),
        )

    def _translate_variable_length_traversal(self, query: Query) -> ReadQuery:
        # Placeholder only: recursive traversal translation is intentionally not implemented yet.
        raise NotImplementedError(
            "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement."
        )
