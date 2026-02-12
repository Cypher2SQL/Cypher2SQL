from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List

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
        nodes = pattern.nodes
        edges = pattern.edges
        if len(edges) > 1:
            return self._translate_multi_hop_traversal(query)
        if not nodes:
            raise ValueError("Cypher pattern contains no nodes.")

        node_aliases: Dict[str, str] = {}
        alias_counter = 0
        for node in nodes:
            node_aliases[node.variable] = f"t{alias_counter}"
            alias_counter += 1

        root = nodes[0]
        root_mapping = self.schema.node_for_label(root.label)
        root_alias = node_aliases[root.variable]
        select = SelectQuery.select_from(root_mapping.table, root_alias)
        self._apply_return_projection(select, query.return_items, root_alias, node_aliases)

        for idx, edge in enumerate(edges):
            left = nodes[idx]
            right = nodes[idx + 1]
            edge_mapping = self.schema.edge_for_type(edge.type)
            alias_counter = self._apply_edge(
                select,
                edge_mapping,
                left,
                right,
                node_aliases,
                alias_counter,
            )

        return select

    def _apply_edge(
        self,
        select: SelectQuery,
        edge_mapping: EdgeMapping,
        left: Node,
        right: Node,
        node_aliases: Dict[str, str],
        alias_counter: int,
    ) -> int:
        left_mapping = self.schema.node_for_label(left.label)
        right_mapping = self.schema.node_for_label(right.label)
        left_alias = node_aliases[left.variable]
        right_alias = node_aliases[right.variable]

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
            return alias_counter

        if edge_mapping.relationship_kind is RelationshipKind.SELF_REFERENTIAL:
            join_on_self = f"{left_alias}.{edge_mapping.from_key} = {right_alias}.{edge_mapping.to_key}"
            select.add_join(JoinClause(JoinType.INNER, left_mapping.table, right_alias, join_on_self))
            return alias_counter

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
                return alias_counter
            if right_is_parent:
                join_on = (
                    f"{left_alias}.{edge_mapping.child_foreign_key} = {right_alias}.{edge_mapping.parent_primary_key}"
                )
                select.add_join(JoinClause(JoinType.INNER, right_mapping.table, right_alias, join_on))
                return alias_counter
            raise ValueError(f"Edge mapping labels do not match nodes: {edge_mapping.type}")

        raise ValueError(f"Unknown relationship kind: {edge_mapping.relationship_kind}")

    def _contains_variable_length_traversal(self, raw_cypher: str) -> bool:
        return "[*" in raw_cypher

    def _translate_variable_length_traversal(self, query: Query) -> SelectQuery:
        # Placeholder only: recursive traversal translation is intentionally not implemented yet.
        raise NotImplementedError(
            "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement."
        )

    def _translate_multi_hop_traversal(self, query: Query) -> SelectQuery:
        # Placeholder only: multi-hop traversal planning is intentionally not implemented yet.
        raise NotImplementedError(
            "Multi-hop traversals are not supported yet; traversal planning is a future enhancement."
        )

    def _apply_return_projection(
        self,
        select: SelectQuery,
        return_items: List[ReturnItem],
        root_alias: str,
        node_aliases: Dict[str, str],
    ) -> None:
        if not return_items:
            select.add_select_column(f"{root_alias}.*")
            return
        for item in return_items:
            alias = node_aliases.get(item.variable)
            if alias is None:
                raise ValueError(f"RETURN references unknown variable: {item.variable}")
            if item.property is None:
                select.add_select_column(f"{alias}.*")
            else:
                select.add_select_column(f"{alias}.{item.property}")
