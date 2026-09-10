"""Read IR: bound read query parts, schema-resolved from a parsed :class:`~cypher2sql.cypher_query.Query`
and rendered to SQL.

Mirrors Java's ``com.iisaka.cypher2sql.query.read`` package.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .cypher_query import (
    BinaryExpression,
    CaseExpression,
    ConstantExpression,
    Edge,
    Expression,
    FunctionExpression,
    KnownFunction,
    Node,
    OrderItem,
    ProjectionItem,
    PropertyExpression,
    UnaryExpression,
    VariableExpression,
    WildcardExpression,
)
from .schema import EdgeMapping, NodeMapping, RelationshipKind
from .sql_query import JoinClause, JoinType, SelectQuery


@dataclass(frozen=True)
class BoundNode:
    """A Cypher :class:`~cypher2sql.cypher_query.Node` resolved against a
    :class:`~cypher2sql.schema.NodeMapping` and assigned a SQL table alias.

    Attributes:
        node: the unresolved Cypher node this was bound from.
        mapping: the schema mapping this node's label resolved to.
        alias: the SQL table alias assigned to this node, e.g. ``t0``.
    """

    node: Node
    mapping: NodeMapping
    alias: str

    @property
    def variable(self) -> str | None:
        """The node's Cypher variable name, or ``None`` if anonymous."""
        return self.node.variable

    @property
    def label(self) -> str | None:
        """The node's resolved label."""
        return self.node.label


@dataclass(frozen=True)
class BoundTraversal:
    """A Cypher :class:`~cypher2sql.cypher_query.Edge` resolved against a
    :class:`~cypher2sql.schema.EdgeMapping` and connecting two :class:`BoundNode`\\ s.

    Attributes:
        edge: the unresolved Cypher edge this was bound from.
        mapping: the schema mapping this edge's type resolved to.
        left: the node this traversal starts from, as written in the Cypher pattern.
        right: the node this traversal ends at, as written in the Cypher pattern.
    """

    edge: Edge
    mapping: EdgeMapping
    left: BoundNode
    right: BoundNode

    def apply_to(
        self,
        select: SelectQuery,
        next_join_alias_counter: list[int],
        join_type: JoinType = JoinType.INNER,
    ) -> list[str]:
        """Adds the SQL join(s) for this traversal to ``select``, choosing the join strategy from the
        mapping's :class:`~cypher2sql.schema.RelationshipKind`, and returns the SQL column expression(s)
        this relationship would project if returned by variable.

        Args:
            next_join_alias_counter: single-element counter used to allocate the next ``jN`` join alias.

        Raises:
            ValueError: if the mapping's parent/child labels do not match the bound nodes.
        """
        if self.mapping.relationship_kind is RelationshipKind.JOIN_TABLE:
            return self._apply_join_table(select, next_join_alias_counter, join_type)
        if self.mapping.relationship_kind is RelationshipKind.SELF_REFERENTIAL:
            return self._apply_self_referential(select, join_type)
        if self.mapping.relationship_kind in (RelationshipKind.ONE_TO_MANY, RelationshipKind.MANY_TO_ONE):
            return self._apply_one_to_many(select, join_type)
        raise ValueError(f"Unknown relationship kind: {self.mapping.relationship_kind}")

    def _apply_join_table(
        self, select: SelectQuery, next_join_alias_counter: list[int], join_type: JoinType
    ) -> list[str]:
        join_alias = f"j{next_join_alias_counter[0]}"
        next_join_alias_counter[0] += 1
        join_on_left = self.left.mapping.join_on_columns(
            self.left.alias, self.left.mapping.primary_keys, join_alias, self.mapping.from_join_keys
        )
        select.add_join(JoinClause(join_type, self.mapping.join_table, join_alias, join_on_left))

        join_on_right = self.right.mapping.join_on_columns(
            join_alias, self.mapping.to_join_keys, self.right.alias, self.right.mapping.primary_keys
        )
        select.add_join(JoinClause(join_type, self.right.mapping.qualified_table, self.right.alias, join_on_right))
        return [f"{join_alias}.*"]

    def _apply_self_referential(self, select: SelectQuery, join_type: JoinType) -> list[str]:
        join_on = self.left.mapping.join_on_columns(
            self.left.alias, self.mapping.from_keys, self.right.alias, self.mapping.to_keys
        )
        select.add_join(JoinClause(join_type, self.left.mapping.qualified_table, self.right.alias, join_on))
        return [
            *[f"{self.left.alias}.{column}" for column in self.mapping.from_keys],
            *[f"{self.right.alias}.{column}" for column in self.mapping.to_keys],
        ]

    def _apply_one_to_many(self, select: SelectQuery, join_type: JoinType) -> list[str]:
        left_is_parent = self.left.label == self.mapping.from_label and self.right.label == self.mapping.to_label
        right_is_parent = self.right.label == self.mapping.from_label and self.left.label == self.mapping.to_label
        if left_is_parent:
            join_on = self.right.mapping.join_on_columns(
                self.right.alias,
                self.mapping.child_foreign_keys,
                self.left.alias,
                self.mapping.parent_primary_keys,
            )
            select.add_join(JoinClause(join_type, self.right.mapping.qualified_table, self.right.alias, join_on))
            return [
                *[f"{self.right.alias}.{column}" for column in self.mapping.child_foreign_keys],
                *[f"{self.left.alias}.{column}" for column in self.mapping.parent_primary_keys],
            ]
        if right_is_parent:
            join_on = self.left.mapping.join_on_columns(
                self.left.alias,
                self.mapping.child_foreign_keys,
                self.right.alias,
                self.mapping.parent_primary_keys,
            )
            select.add_join(JoinClause(join_type, self.right.mapping.qualified_table, self.right.alias, join_on))
            return [
                *[f"{self.left.alias}.{column}" for column in self.mapping.child_foreign_keys],
                *[f"{self.right.alias}.{column}" for column in self.mapping.parent_primary_keys],
            ]
        raise ValueError(f"Edge mapping labels do not match nodes: {self.mapping.type}")


@dataclass(frozen=True)
class BoundPattern:
    """A :class:`~cypher2sql.cypher_query.Pattern` whose nodes and edges have all been bound and aliased.

    Attributes:
        nodes: the pattern's bound nodes, in traversal order.
        traversals: the pattern's bound relationship traversals connecting consecutive nodes.
        is_optional: whether this pattern came from an ``OPTIONAL MATCH``, rendered as an outer join.
        local_where_expression: this pattern's own ``WHERE`` predicate (from its enclosing ``MATCH`` clause), or ``None``.

    Raises:
        ValueError: if ``nodes`` does not have exactly one more element than ``traversals``.
    """

    nodes: list[BoundNode]
    traversals: list[BoundTraversal]
    is_optional: bool = False
    local_where_expression: Expression | None = None

    def __post_init__(self) -> None:
        if len(self.nodes) != len(self.traversals) + 1:
            raise ValueError("BoundPattern requires exactly one more node than traversal.")

    @property
    def root(self) -> BoundNode:
        """The first node in the pattern, from which its FROM clause or outer join originates.

        Raises:
            ValueError: if the pattern has no nodes.
        """
        if not self.nodes:
            raise ValueError("BoundPattern has no nodes.")
        return self.nodes[0]


@dataclass(frozen=True)
class FinalStage:
    """The post-``WITH`` projection stage of a query, rendered as an outer ``SELECT`` over the ``WITH``
    clause's own ``SELECT``.

    Attributes:
        where_expression: the ``RETURN``-side ``WHERE`` predicate (from after ``WITH``), or ``None``.
        projection_items: the final ``RETURN`` clause's projected items.
        distinct: whether the final ``RETURN`` specified ``DISTINCT``.
        order_items: the final ``RETURN``'s own ``ORDER BY`` keys.
        skip: the final ``RETURN``'s ``SKIP`` count, or ``None``.
        limit: the final ``RETURN``'s ``LIMIT`` count, or ``None``.
    """

    where_expression: Expression | None
    projection_items: list[ProjectionItem]
    distinct: bool
    order_items: list[OrderItem]
    skip: int | None
    limit: int | None


@dataclass(frozen=True)
class ReadQuery:
    """The bound, ready-to-render form of a :class:`~cypher2sql.cypher_query.Query`: schema-resolved
    patterns plus the read-side clauses (``WHERE``/``RETURN``/``ORDER BY``/``SKIP``/``LIMIT``), with an
    optional :class:`FinalStage` for a query that also has a ``WITH`` clause. :meth:`as_sql` renders this
    to a :class:`~cypher2sql.sql_query.SelectQuery`.

    When ``final_stage`` is set, ``projection_items`` and the surrounding read-clause fields describe the
    ``WITH`` clause's own ``SELECT``, and ``final_stage`` describes the outer ``SELECT`` built from the
    final ``RETURN``.
    """

    patterns: list[BoundPattern]
    where_expression: Expression | None
    projection_items: list[ProjectionItem]
    distinct: bool = False
    order_items: list[OrderItem] = field(default_factory=list)
    skip: int | None = None
    limit: int | None = None
    final_stage: FinalStage | None = None

    @property
    def pattern_count(self) -> int:
        """The number of top-level bound patterns (one ``MATCH``/``OPTIONAL MATCH`` clause each)."""
        return len(self.patterns)

    def pattern_at(self, index: int) -> BoundPattern:
        """The bound pattern at the given index, in ``MATCH``-clause source order."""
        return self.patterns[index]

    def as_sql(self) -> SelectQuery:
        """Renders this read query as a SQL ``SELECT``: an ``INNER JOIN`` chain for the first pattern, a
        ``LEFT JOIN`` chain for each subsequent (necessarily ``OPTIONAL MATCH``) pattern, and -- if this
        query has a :class:`FinalStage` -- an outer ``SELECT`` over the whole thing.

        Raises:
            ValueError: if there are no patterns.
            NotImplementedError: if the query uses a feature not yet translatable to SQL.
        """
        if not self.patterns:
            raise ValueError("No patterns parsed from Cypher query.")
        if self.patterns[0].is_optional:
            raise NotImplementedError("OPTIONAL MATCH cannot be the first clause yet.")

        aliases_by_variable: dict[str, str] = {}
        nodes_by_alias: dict[str, BoundNode] = {}
        self._register_nodes(self.patterns[0], aliases_by_variable, nodes_by_alias)
        for pattern in self.patterns[1:]:
            if not pattern.is_optional:
                raise NotImplementedError(
                    f"Multiple top-level MATCH patterns are not supported yet. Found: {len(self.patterns)}"
                )
            root_variable = pattern.root.variable
            if not root_variable or root_variable not in aliases_by_variable:
                raise NotImplementedError(
                    "OPTIONAL MATCH must reference a variable already bound by a preceding MATCH clause."
                )
            self._register_nodes(pattern, aliases_by_variable, nodes_by_alias)

        base_pattern = self.patterns[0]
        select = SelectQuery.select_from(base_pattern.root.mapping.qualified_table, base_pattern.root.alias)
        if self.distinct:
            select.set_distinct()
        next_join_alias_counter = [len(nodes_by_alias)]
        edge_projections: dict[str, list[str]] = {}
        edge_property_projections: dict[str, dict[str, str]] = {}

        self._apply_traversals(base_pattern, select, JoinType.INNER, next_join_alias_counter, edge_projections, edge_property_projections)
        for pattern in self.patterns[1:]:
            self._apply_traversals(pattern, select, JoinType.LEFT, next_join_alias_counter, edge_projections, edge_property_projections)
            if pattern.local_where_expression is not None:
                if not pattern.traversals:
                    raise NotImplementedError("WHERE on an OPTIONAL MATCH with no relationships is not supported.")
                rendered = self._render_expression(
                    pattern.local_where_expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
                )
                select.and_last_join_condition(rendered)

        self._apply_where(select, self.where_expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections)
        self._apply_return_projection(
            select, self.projection_items, base_pattern.root, aliases_by_variable, nodes_by_alias,
            edge_projections, edge_property_projections,
        )
        self._apply_order_by(select, self.order_items, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections)
        if self.limit is not None:
            select.set_limit(self.limit)
        if self.skip is not None:
            select.set_offset(self.skip)
        if self.final_stage is None:
            return select
        return self._render_final_stage(select, aliases_by_variable, nodes_by_alias, edge_projections)

    def _render_final_stage(
        self,
        inner_select: SelectQuery,
        inner_aliases_by_variable: dict[str, str],
        inner_nodes_by_alias: dict[str, BoundNode],
        inner_edge_projections: dict[str, list[str]],
    ) -> SelectQuery:
        outer = SelectQuery.from_subquery_select(inner_select, "with0")
        aliases_by_variable: dict[str, str] = {}
        nodes_by_alias: dict[str, BoundNode] = {}
        edge_projections: dict[str, list[str]] = {}
        edge_property_projections: dict[str, dict[str, str]] = {}
        saw_node_passthrough = False

        for item in self.projection_items:
            if isinstance(item.expression, VariableExpression) and item.expression.name in inner_aliases_by_variable:
                if saw_node_passthrough:
                    raise NotImplementedError(
                        "WITH can pass through at most one node variable unchanged; alias the rest to a scalar expression."
                    )
                saw_node_passthrough = True
                original = inner_nodes_by_alias[inner_aliases_by_variable[item.expression.name]]
                carried = BoundNode(original.node, original.mapping, "with0")
                aliases_by_variable[item.alias if item.alias is not None else item.expression.name] = "with0"
                nodes_by_alias["with0"] = carried
            elif isinstance(item.expression, VariableExpression) and item.expression.name in inner_edge_projections:
                raise NotImplementedError(
                    f"Relationship variables cannot be passed through WITH yet: {item.expression.name}"
                )
            elif item.alias is None:
                raise NotImplementedError("WITH items must be aliased unless they pass through a plain variable.")
            else:
                edge_projections[item.alias] = [f"with0.{item.alias}"]

        final_stage = self.final_stage
        if final_stage.distinct:
            outer.set_distinct()
        self._apply_where(outer, final_stage.where_expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections)
        self._apply_return_projection(
            outer, final_stage.projection_items, nodes_by_alias.get("with0"),
            aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections,
        )
        self._apply_order_by(outer, final_stage.order_items, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections)
        if final_stage.limit is not None:
            outer.set_limit(final_stage.limit)
        if final_stage.skip is not None:
            outer.set_offset(final_stage.skip)
        return outer

    def _register_nodes(
        self,
        pattern: BoundPattern,
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
    ) -> None:
        for node in pattern.nodes:
            nodes_by_alias.setdefault(node.alias, node)
            if node.variable:
                aliases_by_variable.setdefault(node.variable, node.alias)

    def _apply_traversals(
        self,
        pattern: BoundPattern,
        select: SelectQuery,
        join_type: JoinType,
        next_join_alias_counter: list[int],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> None:
        for traversal in pattern.traversals:
            projection = traversal.apply_to(select, next_join_alias_counter, join_type)
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

    def _apply_where(
        self,
        select: SelectQuery,
        where_expression: Expression | None,
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> None:
        if where_expression is not None:
            select.add_where(self._render_expression(
                where_expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            ))

    def _apply_return_projection(
        self,
        select: SelectQuery,
        projection_items: list[ProjectionItem],
        default_root: BoundNode,
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> None:
        if not projection_items:
            select.add_select_column(f"{default_root.alias}.*")
            return
        for item in projection_items:
            if isinstance(item.expression, VariableExpression):
                edge_columns = edge_projections.get(item.expression.name)
                if edge_columns is not None and item.alias is None:
                    for column in edge_columns:
                        select.add_select_column(column)
                    continue
            rendered = self._render_expression(
                item.expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, True
            )
            select.add_select_column(rendered if item.alias is None else f"{rendered} AS {item.alias}")

    def _apply_order_by(
        self,
        select: SelectQuery,
        order_items: list[OrderItem],
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> None:
        for order_item in order_items:
            rendered = self._render_expression(
                order_item.expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            )
            select.add_order_by(f"{rendered} DESC" if order_item.descending else f"{rendered} ASC")

    def _render_expression(
        self,
        expression: Expression,
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
        top_level_projection: bool,
    ) -> str:
        if isinstance(expression, VariableExpression):
            return self._render_variable(expression.name, aliases_by_variable, nodes_by_alias, edge_projections, top_level_projection)
        if isinstance(expression, PropertyExpression):
            return self._render_property(expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections)
        if isinstance(expression, ConstantExpression):
            return self._render_constant(expression.value)
        if isinstance(expression, FunctionExpression):
            return self._render_function(expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections)
        if isinstance(expression, BinaryExpression):
            left = self._render_expression(
                expression.left, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            )
            right = self._render_expression(
                expression.right, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            )
            return f"({left} {expression.operator} {right})"
        if isinstance(expression, UnaryExpression):
            operand = self._render_expression(
                expression.operand, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            )
            return f"({expression.operator} {operand})"
        if isinstance(expression, CaseExpression):
            return self._render_case(expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections)
        if isinstance(expression, WildcardExpression):
            return "*"
        raise ValueError(f"Unsupported expression: {expression}")

    def _render_property(
        self,
        property_expression: PropertyExpression,
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> str:
        if isinstance(property_expression.receiver, VariableExpression):
            variable = property_expression.receiver.name
            alias = aliases_by_variable.get(variable)
            if alias is not None:
                return nodes_by_alias[alias].mapping.qualified_column(alias, property_expression.property)
            edge_properties = edge_property_projections.get(variable)
            if edge_properties is not None and property_expression.property in edge_properties:
                return edge_properties[property_expression.property]
            if variable not in edge_projections:
                raise ValueError(f"RETURN references unknown variable: {variable}")
            raise ValueError(f"RETURN edge properties are not supported yet: {variable}.{property_expression.property}")
        receiver = self._render_expression(
            property_expression.receiver, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
        )
        return f"{receiver}.{property_expression.property}"

    def _render_variable(
        self,
        variable: str,
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        top_level_projection: bool,
    ) -> str:
        alias = aliases_by_variable.get(variable)
        if alias is not None:
            node = nodes_by_alias[alias]
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
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> str:
        known = KnownFunction.for_name(function.name)
        if known is None:
            raise NotImplementedError(f"Function is parsed but not rendered yet: {function.name}")
        sql_name = known.sql_name
        arguments = [
            self._render_expression(
                argument, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            )
            for argument in function.arguments
        ]
        return f"{sql_name}({', '.join(arguments)})"

    def _render_case(
        self,
        expression: CaseExpression,
        aliases_by_variable: dict[str, str],
        nodes_by_alias: dict[str, BoundNode],
        edge_projections: dict[str, list[str]],
        edge_property_projections: dict[str, dict[str, str]],
    ) -> str:
        parts = ["CASE"]
        if expression.subject is not None:
            parts.append(self._render_expression(
                expression.subject, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            ))
        for when_expression, then_expression in expression.when_thens:
            parts.append("WHEN")
            parts.append(self._render_expression(
                when_expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            ))
            parts.append("THEN")
            parts.append(self._render_expression(
                then_expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            ))
        if expression.else_expression is not None:
            parts.append("ELSE")
            parts.append(self._render_expression(
                expression.else_expression, aliases_by_variable, nodes_by_alias, edge_projections, edge_property_projections, False
            ))
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
