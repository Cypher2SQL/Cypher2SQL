from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .cypher_query import (
    BinaryExpression,
    CaseExpression,
    Direction,
    Edge,
    Expression,
    FunctionExpression,
    Node,
    Pattern,
    ProjectionItem,
    PropertyExpression,
    Query,
    UnaryExpression,
)
from .read_query import BoundNode, BoundPattern, BoundTraversal, FinalStage, ReadQuery
from .schema import SchemaDefinition, EdgeMapping
from .sql_query import SelectQuery


_AGGREGATE_FUNCTIONS = {"count", "sum", "avg", "min", "max"}


def _is_aggregate(expression: Expression) -> bool:
    if isinstance(expression, FunctionExpression):
        return expression.name.lower() in _AGGREGATE_FUNCTIONS or any(_is_aggregate(arg) for arg in expression.arguments)
    if isinstance(expression, PropertyExpression):
        return _is_aggregate(expression.receiver)
    if isinstance(expression, BinaryExpression):
        return _is_aggregate(expression.left) or _is_aggregate(expression.right)
    if isinstance(expression, UnaryExpression):
        return _is_aggregate(expression.operand)
    if isinstance(expression, CaseExpression):
        return (
            (expression.subject is not None and _is_aggregate(expression.subject))
            or any(_is_aggregate(when) or _is_aggregate(then) for when, then in expression.when_thens)
            or (expression.else_expression is not None and _is_aggregate(expression.else_expression))
        )
    return False


def _has_mixed_aggregation(items: list[ProjectionItem]) -> bool:
    any_aggregate = any(_is_aggregate(item.expression) for item in items)
    any_non_aggregate = any(not _is_aggregate(item.expression) for item in items)
    return any_aggregate and any_non_aggregate


@dataclass
class Mapping:
    schema: SchemaDefinition

    def to_sql(self, query: Query) -> SelectQuery:
        return self.to_read_query(query).as_sql()

    def to_read_query(self, query: Query) -> ReadQuery:
        if query.has_variable_length_traversal:
            return self._translate_variable_length_traversal(query)
        if query.has_with_clause:
            if query.has_multiple_with_clauses:
                raise NotImplementedError("Only one WITH clause is supported yet.")
            if query.has_match_after_with:
                raise NotImplementedError("MATCH after WITH is not supported yet.")
            if _has_mixed_aggregation(query.with_projection_items):
                raise NotImplementedError(
                    "Aggregation grouping in WITH is not supported yet; all WITH items must be aggregate expressions, or none."
                )

        bound_by_variable: dict[str, BoundNode] = {}
        next_alias_index = [0]
        bound_patterns: list[BoundPattern] = []
        for pattern in query.patterns:
            substituted_nodes = self._substitute_bound_labels(pattern.nodes, bound_by_variable)
            nodes = self._resolve_node_labels(Pattern(nodes=substituted_nodes, edges=pattern.edges))
            if not nodes:
                raise ValueError("Cypher pattern contains no nodes.")

            bound_nodes: list[BoundNode] = []
            for node in nodes:
                existing = bound_by_variable.get(node.variable) if node.variable else None
                if existing is not None:
                    bound_node = existing
                else:
                    bound_node = BoundNode(node, self.schema.node_for_label(node.label), f"t{next_alias_index[0]}")
                    next_alias_index[0] += 1
                    if node.variable:
                        bound_by_variable[node.variable] = bound_node
                bound_nodes.append(bound_node)

            traversals = [
                BoundTraversal(
                    edge,
                    self._resolve_relation(edge, nodes[idx], nodes[idx + 1]),
                    bound_nodes[idx],
                    bound_nodes[idx + 1],
                )
                for idx, edge in enumerate(pattern.edges)
            ]
            bound_patterns.append(BoundPattern(bound_nodes, traversals, pattern.is_optional, pattern.local_where_expression))

        if not query.has_with_clause:
            return ReadQuery(
                bound_patterns,
                query.where_expression,
                query.projection_items,
                query.distinct,
                query.order_items,
                query.skip,
                query.limit,
            )
        return ReadQuery(
            bound_patterns,
            query.where_expression,
            query.with_projection_items,
            query.with_distinct,
            query.with_order_items,
            query.with_skip,
            query.with_limit,
            FinalStage(
                query.with_where_expression,
                query.projection_items,
                query.distinct,
                query.order_items,
                query.skip,
                query.limit,
            ),
        )

    def _substitute_bound_labels(self, nodes: list[Node], bound_by_variable: dict[str, BoundNode]) -> list[Node]:
        substituted: list[Node] = []
        for node in nodes:
            existing = bound_by_variable.get(node.variable) if node.variable else None
            if not node.label and existing is not None:
                substituted.append(Node(variable=node.variable, label=existing.label))
            else:
                substituted.append(node)
        return substituted

    def _translate_variable_length_traversal(self, query: Query) -> ReadQuery:
        # Placeholder only: recursive traversal translation is intentionally not implemented yet.
        raise NotImplementedError(
            "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement."
        )

    def _resolve_node_labels(self, pattern: Pattern) -> list[Node]:
        resolved: list[Node] = []
        edges = pattern.edges
        edge_mappings = [
            self._resolve_edge_mapping_for_inference(edge, pattern.nodes[idx], pattern.nodes[idx + 1])
            for idx, edge in enumerate(edges)
        ]
        for idx, node in enumerate(pattern.nodes):
            if node.label:
                resolved.append(node)
                continue
            inferred = None
            if idx > 0:
                prev_edge = edges[idx - 1]
                prev_mapping = edge_mappings[idx - 1]
                prev_candidate = prev_mapping.from_label if prev_edge.direction is Direction.RIGHT_TO_LEFT else prev_mapping.to_label
                inferred = self._merge_label(inferred, prev_candidate, idx)
            if idx < len(edge_mappings):
                next_edge = edges[idx]
                next_mapping = edge_mappings[idx]
                next_candidate = next_mapping.to_label if next_edge.direction is Direction.RIGHT_TO_LEFT else next_mapping.from_label
                inferred = self._merge_label(inferred, next_candidate, idx)
            resolved.append(Node(variable=node.variable, label=inferred))
        return resolved

    def _resolve_edge_mapping_for_inference(self, edge: Edge, left: Node, right: Node) -> EdgeMapping:
        has_left = bool(left.label)
        has_right = bool(right.label)
        if not has_left or not has_right:
            return self.schema.edge_for_type(edge.type)
        if edge.direction is Direction.LEFT_TO_RIGHT:
            return self._edge_for_directed_labels_or_fallback(edge.type, left.label, right.label)
        if edge.direction is Direction.RIGHT_TO_LEFT:
            return self._edge_for_directed_labels_or_fallback(edge.type, right.label, left.label)
        return self.schema.edge_for_type_undirected(edge.type, left.label, right.label)

    def _merge_label(self, current: str | None, candidate: str | None, node_index: int) -> str | None:
        if not candidate:
            return current
        if current is None:
            return candidate
        if current != candidate:
            raise ValueError(f"Unable to infer unique label for anonymous node at index {node_index}")
        return current

    def _resolve_relation(
        self,
        edge: Any,
        left: Node,
        right: Node,
    ) -> EdgeMapping:
        if edge.direction is Direction.LEFT_TO_RIGHT:
            return self._edge_for_directed_labels_or_fallback(edge.type, left.label, right.label)
        if edge.direction is Direction.RIGHT_TO_LEFT:
            return self._edge_for_directed_labels_or_fallback(edge.type, right.label, left.label)
        return self.schema.edge_for_type_undirected(edge.type, left.label, right.label)

    def _edge_for_directed_labels_or_fallback(self, type: str, from_label: str | None, to_label: str | None) -> EdgeMapping:
        try:
            return self.schema.edge_for_type_with_labels(type, from_label, to_label)
        except ValueError as directed_missing:
            try:
                return self.schema.edge_for_type(type)
            except ValueError:
                raise ValueError(f"Edge mapping labels do not match nodes: {type}") from directed_missing
