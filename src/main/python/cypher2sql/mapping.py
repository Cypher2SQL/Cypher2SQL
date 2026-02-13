from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Tuple

from .cypher_query import Query, Pattern, Node, ReturnItem
from .schema import SchemaDefinition, EdgeMapping, RelationshipKind
from .sql_query import JoinType, JoinClause, SelectQuery


@dataclass
class Mapping:
    schema: SchemaDefinition

    def to_sql(self, query: Query) -> SelectQuery:
        if self._contains_variable_length_traversal(query.raw):
            return self._translate_variable_length_traversal(query)

        patterns = query.patterns
        if not patterns:
            raise ValueError("No patterns parsed from Cypher query.")

        pattern: Pattern = patterns[0]
        nodes = self._resolve_node_labels(pattern)
        edges = pattern.edges
        if not nodes:
            raise ValueError("Cypher pattern contains no nodes.")

        aliases_by_index, aliases_by_variable = self._assign_node_aliases(nodes)
        alias_counter = len(aliases_by_index)

        root = nodes[0]
        root_mapping = self.schema.node_for_label(root.label)
        root_alias = aliases_by_index[0]
        select = SelectQuery.select_from(root_mapping.table, root_alias)
        edge_projections: Dict[str, List[str]] = {}

        for idx, edge in enumerate(edges):
            left = nodes[idx]
            right = nodes[idx + 1]
            edge_mapping = self.schema.edge_for_type(edge.type)
            alias_counter, edge_columns = self._apply_edge(
                select,
                edge_mapping,
                left,
                right,
                aliases_by_index[idx],
                aliases_by_index[idx + 1],
                alias_counter,
            )
            if edge.variable:
                edge_projections[edge.variable] = edge_columns

        self._apply_return_projection(select, query.return_items, root_alias, aliases_by_variable, edge_projections)

        return select

    def _apply_edge(
        self,
        select: SelectQuery,
        edge_mapping: EdgeMapping,
        left: Node,
        right: Node,
        left_alias: str,
        right_alias: str,
        alias_counter: int,
    ) -> Tuple[int, List[str]]:
        left_mapping = self.schema.node_for_label(left.label)
        right_mapping = self.schema.node_for_label(right.label)

        if edge_mapping.relationship_kind is RelationshipKind.JOIN_TABLE:
            join_alias = f"j{alias_counter}"
            alias_counter += 1
            join_on_left = (
                f"{left_alias}.{left_mapping.primary_key} = {join_alias}.{edge_mapping.from_join_key}"
            )
            select.add_join(JoinClause(JoinType.INNER, edge_mapping.join_table, join_alias, join_on_left))

            join_on_right = (
                f"{join_alias}.{edge_mapping.to_join_key} = {right_alias}.{right_mapping.primary_key}"
            )
            select.add_join(JoinClause(JoinType.INNER, right_mapping.table, right_alias, join_on_right))
            return alias_counter, [f"{join_alias}.*"]

        if edge_mapping.relationship_kind is RelationshipKind.SELF_REFERENTIAL:
            join_on_self = f"{left_alias}.{edge_mapping.from_key} = {right_alias}.{edge_mapping.to_key}"
            select.add_join(JoinClause(JoinType.INNER, left_mapping.table, right_alias, join_on_self))
            return alias_counter, [
                f"{left_alias}.{edge_mapping.from_key}",
                f"{right_alias}.{edge_mapping.to_key}",
            ]

        if edge_mapping.relationship_kind is RelationshipKind.ONE_TO_MANY:
            parent_label = edge_mapping.from_label
            child_label = edge_mapping.to_label
            left_is_parent = left.label == parent_label and right.label == child_label
            right_is_parent = right.label == parent_label and left.label == child_label
            if left_is_parent:
                join_on = (
                    f"{right_alias}.{edge_mapping.child_foreign_key} = {left_alias}.{edge_mapping.parent_primary_key}"
                )
                select.add_join(JoinClause(JoinType.INNER, right_mapping.table, right_alias, join_on))
                return alias_counter, [
                    f"{right_alias}.{edge_mapping.child_foreign_key}",
                    f"{left_alias}.{edge_mapping.parent_primary_key}",
                ]
            if right_is_parent:
                join_on = (
                    f"{left_alias}.{edge_mapping.child_foreign_key} = {right_alias}.{edge_mapping.parent_primary_key}"
                )
                select.add_join(JoinClause(JoinType.INNER, right_mapping.table, right_alias, join_on))
                return alias_counter, [
                    f"{left_alias}.{edge_mapping.child_foreign_key}",
                    f"{right_alias}.{edge_mapping.parent_primary_key}",
                ]
            raise ValueError(f"Edge mapping labels do not match nodes: {edge_mapping.type}")

        raise ValueError(f"Unknown relationship kind: {edge_mapping.relationship_kind}")

    def _contains_variable_length_traversal(self, raw_cypher: str) -> bool:
        return "[*" in raw_cypher

    def _translate_variable_length_traversal(self, query: Query) -> SelectQuery:
        # Placeholder only: recursive traversal translation is intentionally not implemented yet.
        raise NotImplementedError(
            "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement."
        )

    def _apply_return_projection(
        self,
        select: SelectQuery,
        return_items: List[ReturnItem],
        root_alias: str,
        node_aliases: Dict[str, str],
        edge_projections: Dict[str, List[str]],
    ) -> None:
        if not return_items:
            select.add_select_column(f"{root_alias}.*")
            return
        for item in return_items:
            alias = node_aliases.get(item.variable)
            if alias is not None:
                if item.property is None:
                    select.add_select_column(f"{alias}.*")
                else:
                    select.add_select_column(f"{alias}.{item.property}")
                continue

            edge_columns = edge_projections.get(item.variable)
            if edge_columns is not None:
                if item.property is not None:
                    raise ValueError(f"RETURN edge properties are not supported yet: {item.variable}.{item.property}")
                for column in edge_columns:
                    select.add_select_column(column)
                continue

            raise ValueError(f"RETURN references unknown variable: {item.variable}")

    def _resolve_node_labels(self, pattern: Pattern) -> List[Node]:
        resolved: List[Node] = []
        edges = pattern.edges
        edge_mappings = [self.schema.edge_for_type(edge.type) for edge in edges]
        for idx, node in enumerate(pattern.nodes):
            if node.label:
                resolved.append(node)
                continue
            inferred = None
            if idx > 0:
                inferred = self._merge_label(inferred, edge_mappings[idx - 1].to_label, idx)
            if idx < len(edge_mappings):
                inferred = self._merge_label(inferred, edge_mappings[idx].from_label, idx)
            resolved.append(Node(variable=node.variable, label=inferred))
        return resolved

    def _merge_label(self, current: str | None, candidate: str | None, node_index: int) -> str | None:
        if not candidate:
            return current
        if current is None:
            return candidate
        if current != candidate:
            raise ValueError(f"Unable to infer unique label for anonymous node at index {node_index}")
        return current

    def _assign_node_aliases(self, nodes: List[Node]) -> Tuple[List[str], Dict[str, str]]:
        aliases_by_index: List[str] = []
        aliases_by_variable: Dict[str, str] = {}
        for idx, node in enumerate(nodes):
            alias = f"t{idx}"
            aliases_by_index.append(alias)
            if node.variable is not None:
                aliases_by_variable[node.variable] = alias
        return aliases_by_index, aliases_by_variable
