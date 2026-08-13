from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .cypher_query import Direction, Edge, Node, Pattern, Query
from .read_query import BoundNode, BoundPattern, BoundTraversal, ReadQuery
from .schema import SchemaDefinition, EdgeMapping
from .sql_query import SelectQuery


@dataclass
class Mapping:
    schema: SchemaDefinition

    def to_sql(self, query: Query) -> SelectQuery:
        return self.to_read_query(query).as_sql()

    def to_read_query(self, query: Query) -> ReadQuery:
        if query.has_variable_length_traversal:
            return self._translate_variable_length_traversal(query)
        if query.has_with_clause:
            raise NotImplementedError(
                "WITH clauses are parsed but not rendered yet; pipeline semantics are a future enhancement."
            )

        bound_patterns: list[BoundPattern] = []
        for pattern_index, pattern in enumerate(query.patterns):
            nodes = self._resolve_node_labels(pattern)
            if not nodes:
                raise ValueError("Cypher pattern contains no nodes.")

            bound_nodes = [
                BoundNode(node, self.schema.node_for_label(node.label), self._alias_at(pattern_index, node_index))
                for node_index, node in enumerate(nodes)
            ]
            traversals = [
                BoundTraversal(
                    edge,
                    self._resolve_relation(edge, nodes[idx], nodes[idx + 1]),
                    bound_nodes[idx],
                    bound_nodes[idx + 1],
                )
                for idx, edge in enumerate(pattern.edges)
            ]
            bound_patterns.append(BoundPattern(bound_nodes, traversals))

        return ReadQuery(bound_patterns, query.where_expression, query.projection_items)

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

    def _alias_at(self, pattern_index: int, node_index: int) -> str:
        return f"t{node_index}"
