from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .cypher_query import (
    BinaryExpression,
    CaseExpression,
    ConstantExpression,
    Edge,
    Expression,
    FunctionExpression,
    Node,
    ProjectionItem,
    PropertyExpression,
    UnaryExpression,
    VariableExpression,
    WildcardExpression,
)
from .schema import EdgeMapping, NodeMapping, RelationshipKind
from .sql_query import JoinClause, JoinType, SelectQuery


_SUPPORTED_FUNCTIONS = {
    "count": "COUNT",
    "sum": "SUM",
    "avg": "AVG",
    "min": "MIN",
    "max": "MAX",
    "coalesce": "COALESCE",
    "abs": "ABS",
    "ceil": "CEIL",
    "floor": "FLOOR",
    "round": "ROUND",
    "sqrt": "SQRT",
    "log": "LOG",
    "log10": "LOG10",
    "exp": "EXP",
    "sin": "SIN",
    "cos": "COS",
    "tan": "TAN",
    "trim": "TRIM",
    "ltrim": "LTRIM",
    "rtrim": "RTRIM",
    "substring": "SUBSTRING",
    "replace": "REPLACE",
    "left": "LEFT",
    "right": "RIGHT",
    "toupper": "UPPER",
    "tolower": "LOWER",
}


@dataclass(frozen=True)
class BoundNode:
    node: Node
    mapping: NodeMapping
    alias: str

    @property
    def variable(self) -> str | None:
        return self.node.variable

    @property
    def label(self) -> str | None:
        return self.node.label


@dataclass(frozen=True)
class BoundTraversal:
    edge: Edge
    mapping: EdgeMapping
    left: BoundNode
    right: BoundNode

    def apply_to(self, select: SelectQuery, next_join_alias_counter: list[int]) -> list[str]:
        if self.mapping.relationship_kind is RelationshipKind.JOIN_TABLE:
            return self._apply_join_table(select, next_join_alias_counter)
        if self.mapping.relationship_kind is RelationshipKind.SELF_REFERENTIAL:
            return self._apply_self_referential(select)
        if self.mapping.relationship_kind in (RelationshipKind.ONE_TO_MANY, RelationshipKind.MANY_TO_ONE):
            return self._apply_one_to_many(select)
        raise ValueError(f"Unknown relationship kind: {self.mapping.relationship_kind}")

    def _apply_join_table(self, select: SelectQuery, next_join_alias_counter: list[int]) -> list[str]:
        join_alias = f"j{next_join_alias_counter[0]}"
        next_join_alias_counter[0] += 1
        join_on_left = _join_on_columns(self.left.alias, self.left.mapping.primary_keys, join_alias, self.mapping.from_join_keys)
        select.add_join(JoinClause(JoinType.INNER, self.mapping.join_table, join_alias, join_on_left))

        join_on_right = _join_on_columns(join_alias, self.mapping.to_join_keys, self.right.alias, self.right.mapping.primary_keys)
        select.add_join(JoinClause(JoinType.INNER, self.right.mapping.qualified_table, self.right.alias, join_on_right))
        return [f"{join_alias}.*"]

    def _apply_self_referential(self, select: SelectQuery) -> list[str]:
        join_on = _join_on_columns(self.left.alias, self.mapping.from_keys, self.right.alias, self.mapping.to_keys)
        select.add_join(JoinClause(JoinType.INNER, self.left.mapping.qualified_table, self.right.alias, join_on))
        return [
            *[f"{self.left.alias}.{column}" for column in self.mapping.from_keys],
            *[f"{self.right.alias}.{column}" for column in self.mapping.to_keys],
        ]

    def _apply_one_to_many(self, select: SelectQuery) -> list[str]:
        left_is_parent = self.left.label == self.mapping.from_label and self.right.label == self.mapping.to_label
        right_is_parent = self.right.label == self.mapping.from_label and self.left.label == self.mapping.to_label
        if left_is_parent:
            join_on = (
                _join_on_columns(
                    self.right.alias,
                    self.mapping.child_foreign_keys,
                    self.left.alias,
                    self.mapping.parent_primary_keys,
                )
            )
            select.add_join(JoinClause(JoinType.INNER, self.right.mapping.qualified_table, self.right.alias, join_on))
            return [
                *[f"{self.right.alias}.{column}" for column in self.mapping.child_foreign_keys],
                *[f"{self.left.alias}.{column}" for column in self.mapping.parent_primary_keys],
            ]
        if right_is_parent:
            join_on = (
                _join_on_columns(
                    self.left.alias,
                    self.mapping.child_foreign_keys,
                    self.right.alias,
                    self.mapping.parent_primary_keys,
                )
            )
            select.add_join(JoinClause(JoinType.INNER, self.right.mapping.qualified_table, self.right.alias, join_on))
            return [
                *[f"{self.left.alias}.{column}" for column in self.mapping.child_foreign_keys],
                *[f"{self.right.alias}.{column}" for column in self.mapping.parent_primary_keys],
            ]
        raise ValueError(f"Edge mapping labels do not match nodes: {self.mapping.type}")


@dataclass(frozen=True)
class BoundPattern:
    nodes: list[BoundNode]
    traversals: list[BoundTraversal]

    def __post_init__(self) -> None:
        if len(self.nodes) != len(self.traversals) + 1:
            raise ValueError("BoundPattern requires exactly one more node than traversal.")

    @property
    def root(self) -> BoundNode:
        if not self.nodes:
            raise ValueError("BoundPattern has no nodes.")
        return self.nodes[0]

    def alias_for_variable(self, variable: str | None) -> str | None:
        if not variable:
            return None
        for node in self.nodes:
            if node.variable == variable:
                return node.alias
        return None

    def as_sql(self, where_expression: Expression | None, projection_items: list[ProjectionItem]) -> SelectQuery:
        select = SelectQuery.select_from(self.root.mapping.qualified_table, self.root.alias)
        next_join_alias_counter = [len(self.nodes)]
        edge_projections: dict[str, list[str]] = {}
        edge_property_projections: dict[str, dict[str, str]] = {}

        for traversal in self.traversals:
            projection = traversal.apply_to(select, next_join_alias_counter)
            if traversal.edge.variable:
                edge_projections[traversal.edge.variable] = projection
                if (
                    traversal.mapping.relationship_kind is RelationshipKind.JOIN_TABLE
                    and len(projection) == 1
                    and projection[0].endswith(".*")
                ):
                    join_alias = projection[0][:-2]
                    edge_property_projections[traversal.edge.variable] = {
                        prop: f"{join_alias}.{mapping.column}"
                        for prop, mapping in traversal.mapping.properties.items()
                    }

        self._apply_where(select, where_expression, edge_projections, edge_property_projections)
        self._apply_return_projection(select, projection_items, edge_projections, edge_property_projections)
        return select

    def _apply_where(
        self,
        select: SelectQuery,
        where_expression: Expression | None,
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> None:
        if where_expression is not None:
            select.add_where(self._render_expression(where_expression, edge_projections, edge_property_projections, False))

    def _apply_return_projection(
        self,
        select: SelectQuery,
        projection_items: list[ProjectionItem],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> None:
        if not projection_items:
            select.add_select_column(f"{self.root.alias}.*")
            return
        for item in projection_items:
            if isinstance(item.expression, VariableExpression):
                edge_columns = edge_projections.get(item.expression.name)
                if edge_columns is not None and item.alias is None:
                    for column in edge_columns:
                        select.add_select_column(column)
                    continue
            rendered = self._render_expression(item.expression, edge_projections, edge_property_projections, True)
            select.add_select_column(rendered if item.alias is None else f"{rendered} AS {item.alias}")

    def _render_expression(
        self,
        expression: Expression,
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
        top_level_projection: bool,
    ) -> str:
        if isinstance(expression, VariableExpression):
            return self._render_variable(expression.name, edge_projections, top_level_projection)
        if isinstance(expression, PropertyExpression):
            return self._render_property(expression, edge_projections, edge_property_projections)
        if isinstance(expression, ConstantExpression):
            return self._render_constant(expression.value)
        if isinstance(expression, FunctionExpression):
            return self._render_function(expression, edge_projections, edge_property_projections)
        if isinstance(expression, BinaryExpression):
            left = self._render_expression(expression.left, edge_projections, edge_property_projections, False)
            right = self._render_expression(expression.right, edge_projections, edge_property_projections, False)
            return f"({left} {expression.operator} {right})"
        if isinstance(expression, UnaryExpression):
            operand = self._render_expression(expression.operand, edge_projections, edge_property_projections, False)
            return f"({expression.operator} {operand})"
        if isinstance(expression, CaseExpression):
            return self._render_case(expression, edge_projections, edge_property_projections)
        if isinstance(expression, WildcardExpression):
            return "*"
        raise ValueError(f"Unsupported expression: {expression}")

    def _render_property(
        self,
        property_expression: PropertyExpression,
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> str:
        if isinstance(property_expression.receiver, VariableExpression):
            variable = property_expression.receiver.name
            alias = self.alias_for_variable(variable)
            if alias is not None:
                return self._node_for_alias(alias).mapping.qualified_column(alias, property_expression.property)
            edge_properties = edge_property_projections.get(variable)
            if edge_properties is not None and property_expression.property in edge_properties:
                return edge_properties[property_expression.property]
            if variable not in edge_projections:
                raise ValueError(f"RETURN references unknown variable: {variable}")
            raise ValueError(f"RETURN edge properties are not supported yet: {variable}.{property_expression.property}")
        receiver = self._render_expression(property_expression.receiver, edge_projections, edge_property_projections, False)
        return f"{receiver}.{property_expression.property}"

    def _render_variable(
        self,
        variable: str,
        edge_projections: dict[str, list[str]],
        top_level_projection: bool,
    ) -> str:
        alias = self.alias_for_variable(variable)
        if alias is not None:
            node = self._node_for_alias(alias)
            if top_level_projection:
                return f"{alias}.*"
            return node.mapping.qualified_primary_key(alias)

        edge_columns = edge_projections.get(variable)
        if edge_columns is not None:
            if top_level_projection:
                if len(edge_columns) == 1:
                    return edge_columns[0]
                raise ValueError(f"RETURN edge variable expands to multiple columns: {variable}")
            if len(edge_columns) == 1 and not edge_columns[0].endswith(".*"):
                return edge_columns[0]
            raise ValueError(f"Expression references edge variable that is not scalar: {variable}")

        raise ValueError(f"RETURN references unknown variable: {variable}")

    def _render_function(
        self,
        function: FunctionExpression,
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> str:
        sql_name = _SUPPORTED_FUNCTIONS.get(function.name.lower())
        if sql_name is None:
            raise NotImplementedError(f"Function is parsed but not rendered yet: {function.name}")
        arguments = [
            self._render_expression(argument, edge_projections, edge_property_projections, False)
            for argument in function.arguments
        ]
        return f"{sql_name}({', '.join(arguments)})"

    def _render_case(
        self,
        expression: CaseExpression,
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> str:
        parts = ["CASE"]
        if expression.subject is not None:
            parts.append(self._render_expression(expression.subject, edge_projections, edge_property_projections, False))
        for when_expression, then_expression in expression.when_thens:
            parts.append("WHEN")
            parts.append(self._render_expression(when_expression, edge_projections, edge_property_projections, False))
            parts.append("THEN")
            parts.append(self._render_expression(then_expression, edge_projections, edge_property_projections, False))
        if expression.else_expression is not None:
            parts.append("ELSE")
            parts.append(self._render_expression(expression.else_expression, edge_projections, edge_property_projections, False))
        parts.append("END")
        return " ".join(parts)

    def _render_constant(self, value: Any) -> str:
        if value is None:
            return "NULL"
        if isinstance(value, str):
            return "'" + value.replace("'", "''") + "'"
        if isinstance(value, bool):
            return "TRUE" if value else "FALSE"
        if isinstance(value, float) and value.is_integer():
            return str(int(value))
        return str(value)

    def _node_for_alias(self, alias: str) -> BoundNode:
        for node in self.nodes:
            if node.alias == alias:
                return node
        raise ValueError(f"No node bound for alias: {alias}")


@dataclass(frozen=True)
class ReadQuery:
    patterns: list[BoundPattern]
    where_expression: Expression | None
    projection_items: list[ProjectionItem]

    @property
    def pattern_count(self) -> int:
        return len(self.patterns)

    def pattern_at(self, index: int) -> BoundPattern:
        return self.patterns[index]

    def as_sql(self) -> SelectQuery:
        if not self.patterns:
            raise ValueError("No patterns parsed from Cypher query.")
        if len(self.patterns) > 1:
            raise NotImplementedError(f"Multiple top-level MATCH patterns are not supported yet. Found: {len(self.patterns)}")
        return self.patterns[0].as_sql(self.where_expression, self.projection_items)


def _join_on_columns(left_alias: str, left_columns: list[str], right_alias: str, right_columns: list[str]) -> str:
    if len(left_columns) != len(right_columns):
        raise ValueError(f"Join key arity mismatch: {len(left_columns)} != {len(right_columns)}")
    return " AND ".join(
        f"{left_alias}.{left_column} = {right_alias}.{right_column}"
        for left_column, right_column in zip(left_columns, right_columns, strict=True)
    )
