from __future__ import annotations

import builtins
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

from .schema import EdgeMapping, SchemaDefinition

try:
    from antlr4 import InputStream, CommonTokenStream
    from antlr4.error.ErrorListener import ErrorListener
    from antlr4.tree.Tree import TerminalNode
    from antlr4_cypher import CypherLexer, CypherParser
except ImportError as exc:  # pragma: no cover - runtime dependency
    InputStream = None
    CommonTokenStream = None
    ErrorListener = object
    TerminalNode = object
    CypherLexer = None
    CypherParser = None
    _antlr_import_error = exc


class Direction(Enum):
    LEFT_TO_RIGHT = "LEFT_TO_RIGHT"
    RIGHT_TO_LEFT = "RIGHT_TO_LEFT"
    UNDIRECTED = "UNDIRECTED"


@dataclass(frozen=True)
class Node:
    variable: str | None
    label: str | None


@dataclass(frozen=True)
class Edge:
    variable: str | None
    type: str | None
    direction: Direction


@dataclass(frozen=True)
class Pattern:
    nodes: list[Node]
    edges: list[Edge]

    def bind(
        self,
        schema: SchemaDefinition,
        bound_by_variable: dict[str, Any],
        next_alias_index: list[int],
        is_optional: bool,
        where_expression: "Expression | None",
    ) -> Any:
        from .read_query import BoundNode, BoundPattern, BoundTraversal  # local: avoids a cypher_query <-> read_query import cycle

        substituted_nodes = self._substitute_bound_labels(bound_by_variable)
        nodes = self._resolve_node_labels(schema, substituted_nodes)
        if not nodes:
            raise ValueError("Cypher pattern contains no nodes.")

        bound_nodes = []
        for node in nodes:
            existing = bound_by_variable.get(node.variable) if node.variable else None
            if existing is not None:
                bound_node = existing
            else:
                bound_node = BoundNode(node, schema.node_for_label(node.label), f"t{next_alias_index[0]}")
                next_alias_index[0] += 1
                if node.variable:
                    bound_by_variable[node.variable] = bound_node
            bound_nodes.append(bound_node)

        traversals = [
            BoundTraversal(
                edge,
                self._resolve_relation(schema, edge, nodes[idx], nodes[idx + 1]),
                bound_nodes[idx],
                bound_nodes[idx + 1],
            )
            for idx, edge in enumerate(self.edges)
        ]
        return BoundPattern(bound_nodes, traversals, is_optional, where_expression)

    def _substitute_bound_labels(self, bound_by_variable: dict[str, Any]) -> list[Node]:
        substituted: list[Node] = []
        for node in self.nodes:
            existing = bound_by_variable.get(node.variable) if node.variable else None
            if not node.label and existing is not None:
                substituted.append(Node(variable=node.variable, label=existing.label))
            else:
                substituted.append(node)
        return substituted

    def _resolve_node_labels(self, schema: SchemaDefinition, substituted_nodes: list[Node]) -> list[Node]:
        resolved: list[Node] = []
        edge_mappings = [
            self._resolve_edge_mapping_for_inference(schema, edge, substituted_nodes[idx], substituted_nodes[idx + 1])
            for idx, edge in enumerate(self.edges)
        ]
        for idx, node in enumerate(substituted_nodes):
            if node.label:
                resolved.append(node)
                continue
            inferred = None
            if idx > 0:
                prev_edge = self.edges[idx - 1]
                prev_mapping = edge_mappings[idx - 1]
                prev_candidate = prev_mapping.from_label if prev_edge.direction is Direction.RIGHT_TO_LEFT else prev_mapping.to_label
                inferred = self._merge_label(inferred, prev_candidate, idx)
            if idx < len(edge_mappings):
                next_edge = self.edges[idx]
                next_mapping = edge_mappings[idx]
                next_candidate = next_mapping.to_label if next_edge.direction is Direction.RIGHT_TO_LEFT else next_mapping.from_label
                inferred = self._merge_label(inferred, next_candidate, idx)
            resolved.append(Node(variable=node.variable, label=inferred))
        return resolved

    def _resolve_edge_mapping_for_inference(self, schema: SchemaDefinition, edge: Edge, left: Node, right: Node) -> EdgeMapping:
        has_left = bool(left.label)
        has_right = bool(right.label)
        if not has_left or not has_right:
            return schema.edge_for_type(edge.type)
        if edge.direction is Direction.LEFT_TO_RIGHT:
            return self._edge_for_directed_labels_or_fallback(schema, edge.type, left.label, right.label)
        if edge.direction is Direction.RIGHT_TO_LEFT:
            return self._edge_for_directed_labels_or_fallback(schema, edge.type, right.label, left.label)
        return schema.edge_for_type_undirected(edge.type, left.label, right.label)

    def _merge_label(self, current: str | None, candidate: str | None, node_index: int) -> str | None:
        if not candidate:
            return current
        if current is None:
            return candidate
        if current != candidate:
            raise ValueError(f"Unable to infer unique label for anonymous node at index {node_index}")
        return current

    def _resolve_relation(self, schema: SchemaDefinition, edge: Edge, left: Node, right: Node) -> EdgeMapping:
        if edge.direction is Direction.LEFT_TO_RIGHT:
            return self._edge_for_directed_labels_or_fallback(schema, edge.type, left.label, right.label)
        if edge.direction is Direction.RIGHT_TO_LEFT:
            return self._edge_for_directed_labels_or_fallback(schema, edge.type, right.label, left.label)
        return schema.edge_for_type_undirected(edge.type, left.label, right.label)

    def _edge_for_directed_labels_or_fallback(
        self, schema: SchemaDefinition, type: str, from_label: str | None, to_label: str | None
    ) -> EdgeMapping:
        try:
            return schema.edge_for_type_with_labels(type, from_label, to_label)
        except ValueError as directed_missing:
            try:
                return schema.edge_for_type(type)
            except ValueError:
                raise ValueError(f"Edge mapping labels do not match nodes: {type}") from directed_missing


class KnownFunction(Enum):
    COUNT = ("count", "COUNT", True)
    SUM = ("sum", "SUM", True)
    AVG = ("avg", "AVG", True)
    MIN = ("min", "MIN", True)
    MAX = ("max", "MAX", True)
    COALESCE = ("coalesce", "COALESCE", False)
    ABS = ("abs", "ABS", False)
    CEIL = ("ceil", "CEIL", False)
    FLOOR = ("floor", "FLOOR", False)
    ROUND = ("round", "ROUND", False)
    SQRT = ("sqrt", "SQRT", False)
    LOG = ("log", "LOG", False)
    LOG10 = ("log10", "LOG10", False)
    EXP = ("exp", "EXP", False)
    SIN = ("sin", "SIN", False)
    COS = ("cos", "COS", False)
    TAN = ("tan", "TAN", False)
    TRIM = ("trim", "TRIM", False)
    LTRIM = ("ltrim", "LTRIM", False)
    RTRIM = ("rtrim", "RTRIM", False)
    SUBSTRING = ("substring", "SUBSTRING", False)
    REPLACE = ("replace", "REPLACE", False)
    LEFT = ("left", "LEFT", False)
    RIGHT = ("right", "RIGHT", False)
    TOUPPER = ("toupper", "UPPER", False)
    TOLOWER = ("tolower", "LOWER", False)

    def __init__(self, cypher_name: str, sql_name: str, aggregate: bool) -> None:
        self.cypher_name = cypher_name
        self.sql_name = sql_name
        self.aggregate = aggregate

    @classmethod
    def for_name(cls, cypher_name: str) -> "KnownFunction | None":
        return _KNOWN_FUNCTIONS_BY_CYPHER_NAME.get(cypher_name.lower())


_KNOWN_FUNCTIONS_BY_CYPHER_NAME = {function.cypher_name: function for function in KnownFunction}


@dataclass(frozen=True)
class Expression:
    def is_aggregate(self) -> bool:
        if isinstance(self, FunctionExpression):
            known = KnownFunction.for_name(self.name)
            return (known is not None and known.aggregate) or any(arg.is_aggregate() for arg in self.arguments)
        if isinstance(self, PropertyExpression):
            return self.receiver.is_aggregate()
        if isinstance(self, BinaryExpression):
            return self.left.is_aggregate() or self.right.is_aggregate()
        if isinstance(self, UnaryExpression):
            return self.operand.is_aggregate()
        if isinstance(self, CaseExpression):
            return (
                (self.subject is not None and self.subject.is_aggregate())
                or any(when.is_aggregate() or then.is_aggregate() for when, then in self.when_thens)
                or (self.else_expression is not None and self.else_expression.is_aggregate())
            )
        return False


@dataclass(frozen=True)
class VariableExpression(Expression):
    name: str


@dataclass(frozen=True)
class PropertyExpression(Expression):
    receiver: Expression
    property: str


@dataclass(frozen=True)
class ConstantExpression(Expression):
    value: Any


@dataclass(frozen=True)
class FunctionExpression(Expression):
    name: str
    arguments: list[Expression]


@dataclass(frozen=True)
class BinaryExpression(Expression):
    left: Expression
    operator: str
    right: Expression


@dataclass(frozen=True)
class UnaryExpression(Expression):
    operator: str
    operand: Expression


@dataclass(frozen=True)
class CaseExpression(Expression):
    subject: Expression | None
    when_thens: list[tuple[Expression, Expression]]
    else_expression: Expression | None


@dataclass(frozen=True)
class WildcardExpression(Expression):
    pass


@dataclass(frozen=True)
class ReturnItem:
    variable: str
    property: str | None = None
    alias: str | None = None

    @builtins.property
    def expression(self) -> Expression:
        expression: Expression = VariableExpression(self.variable)
        if self.property is not None:
            expression = PropertyExpression(expression, self.property)
        return expression


@dataclass(frozen=True)
class ProjectionItem:
    expression: Expression
    alias: str | None = None


@dataclass(frozen=True)
class OrderItem:
    expression: Expression
    descending: bool


def _normalize_projection_items(items: list[ReturnItem | ProjectionItem]) -> list[ProjectionItem]:
    return [item if isinstance(item, ProjectionItem) else ProjectionItem(item.expression, item.alias) for item in items]


@dataclass(frozen=True)
class MatchClause:
    patterns: list[Pattern]
    is_optional: bool = False
    where_expression: Expression | None = None
    parse_tree_node: Any = None

    def bind(self, schema: SchemaDefinition, bound_by_variable: dict[str, Any], next_alias_index: list[int]) -> list[Any]:
        return [
            pattern.bind(schema, bound_by_variable, next_alias_index, self.is_optional, self.where_expression)
            for pattern in self.patterns
        ]


@dataclass(frozen=True)
class WithClause:
    items: list[ProjectionItem]
    distinct: bool = False
    order_items: list[OrderItem] = field(default_factory=list)
    skip: int | None = None
    limit: int | None = None
    where_expression: Expression | None = None
    parse_tree_node: Any = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "items", _normalize_projection_items(self.items))

    def has_mixed_aggregation(self) -> bool:
        any_aggregate = any(item.expression.is_aggregate() for item in self.items)
        any_non_aggregate = any(not item.expression.is_aggregate() for item in self.items)
        return any_aggregate and any_non_aggregate


@dataclass(frozen=True)
class ReturnClause:
    items: list[ProjectionItem]
    distinct: bool = False
    order_items: list[OrderItem] = field(default_factory=list)
    skip: int | None = None
    limit: int | None = None
    parse_tree_node: Any = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "items", _normalize_projection_items(self.items))


Clause = MatchClause | WithClause | ReturnClause


class Query:
    def __init__(
        self,
        raw: str,
        clauses: list[Clause],
        parse_tree: Any,
        has_variable_length_traversal: bool | None = None,
    ) -> None:
        self._raw = raw
        self._clauses = list(clauses)
        self._parse_tree = parse_tree
        self._has_variable_length_traversal = (
            ("[*" in raw) if has_variable_length_traversal is None else has_variable_length_traversal
        )

    @property
    def raw(self) -> str:
        return self._raw

    @property
    def clauses(self) -> list[Clause]:
        return list(self._clauses)

    @property
    def parse_tree(self) -> Any:
        return self._parse_tree

    @property
    def has_variable_length_traversal(self) -> bool:
        return self._has_variable_length_traversal

    @property
    def match_clauses(self) -> list[MatchClause]:
        return [clause for clause in self._clauses if isinstance(clause, MatchClause)]

    @property
    def with_clause(self) -> WithClause | None:
        for clause in self._clauses:
            if isinstance(clause, WithClause):
                return clause
        return None

    @property
    def has_with_clause(self) -> bool:
        return self.with_clause is not None

    @property
    def return_clause(self) -> ReturnClause:
        for clause in self._clauses:
            if isinstance(clause, ReturnClause):
                return clause
        return ReturnClause(items=[])

    @property
    def has_multiple_with_clauses(self) -> bool:
        return sum(1 for clause in self._clauses if isinstance(clause, WithClause)) > 1

    @property
    def has_match_after_with(self) -> bool:
        saw_with = False
        for clause in self._clauses:
            if isinstance(clause, WithClause):
                saw_with = True
            elif isinstance(clause, MatchClause) and saw_with:
                return True
        return False

    @classmethod
    def parse(cls, cypher: str) -> "Query":
        parse_tree, parser = _parse(cypher)
        clauses = _extract_clauses(parser, parse_tree)
        return cls(cypher, clauses, parse_tree, _has_variable_length_traversal(parser, parse_tree))


class _CypherSyntaxErrorListener(ErrorListener):
    def syntaxError(self, recognizer, offendingSymbol, line, column, msg, e):  # noqa: N802
        raise ValueError(f"Cypher syntax error at line {line}, column {column}: {msg}") from e


def _parse(cypher: str) -> tuple[Any, Any]:
    if InputStream is None:
        raise RuntimeError(
            "ANTLR runtime not available. Install 'antlr4-python3-runtime' and 'antlr4-cypher'."
        ) from _antlr_import_error

    lexer = CypherLexer(InputStream(cypher))
    tokens = CommonTokenStream(lexer)
    parser = CypherParser(tokens)
    parser.removeErrorListeners()
    parser.addErrorListener(_CypherSyntaxErrorListener())

    for rule in ("statement", "query", "cypher", "oC_Cypher"):
        rule_fn = getattr(parser, rule, None)
        if rule_fn is not None:
            return rule_fn(), parser
    raise RuntimeError("No supported Cypher entry rule found on parser.")


def _extract_projection_items(parser: Any, scope: Any) -> list[ProjectionItem]:
    if scope is None:
        return []
    items: list[ProjectionItem] = []
    stack: list[Any] = [scope]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name is not None and _is_projection_item_rule(rule_name):
            expr, alias = _projection_expression_and_alias(current)
            items.append(ProjectionItem(parse_expression(expr), alias))
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return items


def _extract_order_items(projection_body: Any) -> list[OrderItem]:
    if projection_body is None or projection_body.orderSt() is None:
        return []
    order_items: list[OrderItem] = []
    for order_item in projection_body.orderSt().orderItem():
        descending = order_item.DESC() is not None or order_item.DESCENDING() is not None
        order_items.append(OrderItem(parse_expression(order_item.expression().getText()), descending))
    return order_items


def _extract_skip(projection_body: Any) -> int | None:
    if projection_body is None or projection_body.skipSt() is None:
        return None
    return _require_integer_literal(parse_expression(projection_body.skipSt().expression().getText()), "SKIP")


def _extract_limit(projection_body: Any) -> int | None:
    if projection_body is None or projection_body.limitSt() is None:
        return None
    return _require_integer_literal(parse_expression(projection_body.limitSt().expression().getText()), "LIMIT")


def _extract_distinct(projection_body: Any) -> bool:
    return projection_body is not None and projection_body.DISTINCT() is not None


def _require_integer_literal(expression: Expression, clause: str) -> int:
    if isinstance(expression, ConstantExpression) and isinstance(expression.value, int) and not isinstance(expression.value, bool):
        return expression.value
    raise NotImplementedError(f"{clause} must be an integer literal; parameters are not supported yet.")


def _extract_clauses(parser: Any, parse_tree: Any) -> list[Clause]:
    clauses: list[Clause] = []
    stack: list[Any] = [parse_tree]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name == "matchSt":
            clauses.append(_to_match_clause(parser, current))
        elif rule_name == "withSt":
            clauses.append(_to_with_clause(parser, current))
        elif rule_name == "returnSt":
            clauses.append(_to_return_clause(parser, current))
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return clauses


def _to_match_clause(parser: Any, match_st: Any) -> MatchClause:
    optional = match_st.OPTIONAL() is not None
    pattern_where = match_st.patternWhere()
    where_ctx = pattern_where.where() if pattern_where is not None else None
    where_expression = parse_expression(where_ctx.expression().getText()) if where_ctx is not None else None
    pattern_ctx = pattern_where.pattern() if pattern_where is not None else None
    roots = _find_pattern_elements(parser, pattern_ctx) if pattern_ctx is not None else []

    patterns: list[Pattern] = []
    for root in roots:
        node_contexts: list[Any] = []
        relationship_contexts: list[Any] = []
        stack: list[Any] = [root]
        while stack:
            current = stack.pop()
            rule_name = _rule_name(parser, current)
            if rule_name == "nodePattern":
                node_contexts.append(current)
            elif rule_name == "relationshipPattern":
                relationship_contexts.append(current)
            child_count = getattr(current, "getChildCount", lambda: 0)()
            for idx in range(child_count - 1, -1, -1):
                stack.append(current.getChild(idx))
        if not node_contexts:
            continue

        nodes = [_parse_node_text(ctx.getText()) for ctx in node_contexts]
        edges = [_parse_edge_text(ctx.getText()) for ctx in relationship_contexts]
        patterns.append(Pattern(nodes=nodes, edges=edges))

    return MatchClause(patterns=patterns, is_optional=optional, where_expression=where_expression, parse_tree_node=match_st)


def _to_with_clause(parser: Any, with_st: Any) -> WithClause:
    projection_body = with_st.projectionBody()
    where_expression = (
        parse_expression(with_st.where().expression().getText()) if with_st.where() is not None else None
    )
    return WithClause(
        items=_extract_projection_items(parser, projection_body.projectionItems() if projection_body else None),
        distinct=_extract_distinct(projection_body),
        order_items=_extract_order_items(projection_body),
        skip=_extract_skip(projection_body),
        limit=_extract_limit(projection_body),
        where_expression=where_expression,
        parse_tree_node=with_st,
    )


def _to_return_clause(parser: Any, return_st: Any) -> ReturnClause:
    projection_body = return_st.projectionBody()
    return ReturnClause(
        items=_extract_projection_items(parser, projection_body.projectionItems() if projection_body else None),
        distinct=_extract_distinct(projection_body),
        order_items=_extract_order_items(projection_body),
        skip=_extract_skip(projection_body),
        limit=_extract_limit(projection_body),
        parse_tree_node=return_st,
    )


def _find_pattern_elements(parser: Any, parse_tree: Any) -> list[Any]:
    roots: list[Any] = []
    stack: list[Any] = [parse_tree]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name in ("patternElement", "patternElem"):
            roots.append(current)
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return roots


def _rule_name(parser: Any, context: Any) -> str | None:
    get_rule_index = getattr(context, "getRuleIndex", None)
    if get_rule_index is None:
        return None
    return parser.ruleNames[get_rule_index()]


def _is_projection_item_rule(rule_name: str) -> bool:
    normalized = _normalized_rule_name(rule_name)
    return normalized.endswith("returnitem") or normalized.endswith("projectionitem")


def _projection_expression_and_alias(context: Any) -> tuple[str, str | None]:
    before_alias: list[str] = []
    after_alias: list[str] = []
    saw_as = False
    for i in range(getattr(context, "getChildCount", lambda: 0)()):
        child = context.getChild(i)
        if isinstance(child, TerminalNode) and child.getText().upper() == "AS":
            saw_as = True
            continue
        if saw_as:
            after_alias.append(child.getText())
        else:
            before_alias.append(child.getText())
    if saw_as:
        return "".join(before_alias).strip(), "".join(after_alias).strip() or None
    return context.getText().strip(), None


def _normalized_rule_name(rule_name: str) -> str:
    return "".join(c for c in rule_name if c.isalnum()).lower()


def _parse_node_text(text: str) -> Node:
    open_idx = text.find("(")
    close_idx = text.rfind(")")
    if open_idx < 0 or close_idx < open_idx:
        raise ValueError(f"Unsupported node pattern: {text}")

    inside = text[open_idx + 1 : close_idx]
    properties_at = inside.find("{")
    if properties_at >= 0:
        inside = inside[:properties_at]
    inside = inside.strip()

    if not inside:
        return Node(variable=None, label=None)

    if inside.startswith(":"):
        return Node(variable=None, label=_first_token(inside[1:], "&:{ \t\n\r"))

    colon = inside.find(":")
    variable = (inside[:colon] if colon >= 0 else inside).strip()
    label = _first_token(inside[colon + 1 :], "&:{ \t\n\r") if colon >= 0 else None
    return Node(variable=_empty_to_none(variable), label=label)


def _parse_edge_text(text: str) -> Edge:
    open_idx = text.find("[")
    close_idx = text.rfind("]")
    if open_idx < 0 or close_idx <= open_idx:
        raise ValueError(f"Unsupported relationship pattern: {text}")

    inside = text[open_idx + 1 : close_idx].strip()
    trimmed = inside[1:] if inside.startswith(":") else inside
    colon = trimmed.find(":")

    if colon >= 0:
        variable = _empty_to_none(trimmed[:colon].strip())
        rel_type = _first_token(trimmed[colon + 1 :], "|&:*{ \t\n\r")
    else:
        variable = None
        rel_type = None if "*" in trimmed else _first_token(trimmed, "|&:*{ \t\n\r")

    if "->" in text:
        direction = Direction.LEFT_TO_RIGHT
    elif "<-" in text:
        direction = Direction.RIGHT_TO_LEFT
    else:
        direction = Direction.UNDIRECTED

    return Edge(variable=variable, type=rel_type, direction=direction)


def _first_token(value: str, separators: str) -> str | None:
    if value is None:
        return None
    stop = len(value)
    for ch in separators:
        idx = value.find(ch)
        if idx >= 0:
            stop = min(stop, idx)
    token = value[:stop].strip()
    return token or None


def _empty_to_none(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = value.strip()
    return stripped or None


def _has_variable_length_traversal(parser: Any, parse_tree: Any) -> bool:
    stack: list[Any] = [parse_tree]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name == "relationshipPattern" and "*" in current.getText():
            return True
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return False


def parse_expression(raw: str) -> Expression:
    return _ExpressionParser(raw).parse()


class _TokenKind(Enum):
    IDENTIFIER = "IDENTIFIER"
    NUMBER = "NUMBER"
    STRING = "STRING"
    KEYWORD = "KEYWORD"
    SYMBOL = "SYMBOL"
    EOF = "EOF"


@dataclass(frozen=True)
class _Token:
    kind: _TokenKind
    text: str
    raw: str


class _ExpressionParser:
    _KEYWORDS = {"AND", "OR", "NOT", "CASE", "WHEN", "THEN", "ELSE", "END", "TRUE", "FALSE", "NULL"}
    _COMPARISON = {"=": "=", "<>": "<>", "!=": "<>", "<": "<", "<=": "<=", ">": ">", ">=": ">="}

    def __init__(self, raw: str) -> None:
        self._raw = raw
        self._tokens = self._tokenize(raw)
        self._position = 0

    def parse(self) -> Expression:
        expression = self._parse_case_or_logical()
        self._expect(_TokenKind.EOF)
        return expression

    def _parse_case_or_logical(self) -> Expression:
        if self._match_keyword("CASE"):
            return self._parse_case()
        return self._parse_or()

    def _parse_case(self) -> Expression:
        subject = None if self._peek_keyword("WHEN") else self._parse_or()
        when_thens: list[tuple[Expression, Expression]] = []
        while self._peek_keyword("WHEN"):
            self._expect_keyword("WHEN")
            when_expression = self._parse_or()
            self._expect_keyword("THEN")
            then_expression = self._parse_case_or_logical()
            when_thens.append((when_expression, then_expression))
        else_expression = None
        if self._match_keyword("ELSE"):
            else_expression = self._parse_case_or_logical()
        self._expect_keyword("END")
        return CaseExpression(subject, when_thens, else_expression)

    def _parse_or(self) -> Expression:
        expression = self._parse_and()
        while self._match_keyword("OR"):
            expression = BinaryExpression(expression, "OR", self._parse_and())
        return expression

    def _parse_and(self) -> Expression:
        expression = self._parse_comparison()
        while self._match_keyword("AND"):
            expression = BinaryExpression(expression, "AND", self._parse_comparison())
        return expression

    def _parse_comparison(self) -> Expression:
        expression = self._parse_additive()
        while self._peek().kind is _TokenKind.SYMBOL and self._peek().text in self._COMPARISON:
            operator = self._COMPARISON[self._advance().text]
            expression = BinaryExpression(expression, operator, self._parse_additive())
        return expression

    def _parse_additive(self) -> Expression:
        expression = self._parse_multiplicative()
        while self._peek().kind is _TokenKind.SYMBOL and self._peek().text in {"+", "-"}:
            operator = self._advance().text
            expression = BinaryExpression(expression, operator, self._parse_multiplicative())
        return expression

    def _parse_multiplicative(self) -> Expression:
        expression = self._parse_unary()
        while self._peek().kind is _TokenKind.SYMBOL and self._peek().text in {"*", "/", "%"}:
            operator = self._advance().text
            expression = BinaryExpression(expression, operator, self._parse_unary())
        return expression

    def _parse_unary(self) -> Expression:
        if self._match_keyword("NOT"):
            return UnaryExpression("NOT", self._parse_unary())
        if self._match_symbol("-"):
            return UnaryExpression("-", self._parse_unary())
        if self._match_symbol("+"):
            return UnaryExpression("+", self._parse_unary())
        return self._parse_postfix()

    def _parse_postfix(self) -> Expression:
        expression = self._parse_primary()
        while self._match_symbol("."):
            expression = PropertyExpression(expression, self._expect(_TokenKind.IDENTIFIER).text)
        return expression

    def _parse_primary(self) -> Expression:
        if self._match_symbol("("):
            expression = self._parse_case_or_logical()
            self._expect_symbol(")")
            return expression
        if self._match_symbol("*"):
            return WildcardExpression()
        token = self._peek()
        if token.kind is _TokenKind.NUMBER:
            self._advance()
            return ConstantExpression(float(token.text) if "." in token.text else int(token.text))
        if token.kind is _TokenKind.STRING:
            self._advance()
            return ConstantExpression(token.text)
        if token.kind is _TokenKind.KEYWORD:
            if token.text == "TRUE":
                self._advance()
                return ConstantExpression(True)
            if token.text == "FALSE":
                self._advance()
                return ConstantExpression(False)
            if token.text == "NULL":
                self._advance()
                return ConstantExpression(None)
        identifier = self._expect(_TokenKind.IDENTIFIER)
        if self._match_symbol("("):
            arguments: list[Expression] = []
            if not self._match_symbol(")"):
                while True:
                    arguments.append(self._parse_case_or_logical())
                    if not self._match_symbol(","):
                        break
                self._expect_symbol(")")
            return FunctionExpression(identifier.text, arguments)
        return VariableExpression(identifier.text)

    def _match_keyword(self, keyword: str) -> bool:
        if self._peek_keyword(keyword):
            self._advance()
            return True
        return False

    def _peek_keyword(self, keyword: str) -> bool:
        token = self._peek()
        return token.kind is _TokenKind.KEYWORD and token.text == keyword

    def _expect_keyword(self, keyword: str) -> None:
        if not self._match_keyword(keyword):
            raise self._unsupported()

    def _match_symbol(self, symbol: str) -> bool:
        token = self._peek()
        if token.kind is _TokenKind.SYMBOL and token.text == symbol:
            self._advance()
            return True
        return False

    def _expect_symbol(self, symbol: str) -> None:
        if not self._match_symbol(symbol):
            raise self._unsupported()

    def _expect(self, kind: _TokenKind) -> _Token:
        token = self._peek()
        if token.kind is not kind:
            raise self._unsupported()
        return self._advance()

    def _peek(self) -> _Token:
        return self._tokens[self._position]

    def _advance(self) -> _Token:
        token = self._tokens[self._position]
        self._position += 1
        return token

    def _unsupported(self) -> ValueError:
        remaining = "".join(token.raw for token in self._tokens[self._position:] if token.kind is not _TokenKind.EOF)
        return ValueError(f"Unsupported RETURN expression: {remaining}")

    @classmethod
    def _tokenize(cls, raw: str) -> list[_Token]:
        tokens: list[_Token] = []
        i = 0
        while i < len(raw):
            c = raw[i]
            if c.isspace():
                i += 1
                continue
            if c == "'":
                value: list[str] = []
                i += 1
                while i < len(raw):
                    current = raw[i]
                    if current == "'" and i + 1 < len(raw) and raw[i + 1] == "'":
                        value.append("'")
                        i += 2
                        continue
                    if current == "'":
                        i += 1
                        break
                    value.append(current)
                    i += 1
                text = "".join(value)
                tokens.append(_Token(_TokenKind.STRING, text, "'" + text.replace("'", "''") + "'"))
                continue
            if c == '"':
                value = []
                i += 1
                while i < len(raw):
                    current = raw[i]
                    if current == '"' and i + 1 < len(raw) and raw[i + 1] == '"':
                        value.append('"')
                        i += 2
                        continue
                    if current == '"':
                        i += 1
                        break
                    value.append(current)
                    i += 1
                text = "".join(value)
                tokens.append(_Token(_TokenKind.STRING, text, "'" + text.replace("'", "''") + "'"))
                continue
            if c.isdigit():
                start = i
                while i < len(raw) and (raw[i].isdigit() or raw[i] == "."):
                    i += 1
                text = raw[start:i]
                tokens.append(_Token(_TokenKind.NUMBER, text, text))
                continue
            if c.isalpha() or c == "_":
                start = i
                while i < len(raw) and (raw[i].isalnum() or raw[i] == "_"):
                    i += 1
                text = raw[start:i]
                upper = text.upper()
                kind = _TokenKind.KEYWORD if upper in cls._KEYWORDS else _TokenKind.IDENTIFIER
                tokens.append(_Token(kind, upper if kind is _TokenKind.KEYWORD else text, text))
                continue
            if i + 1 < len(raw) and raw[i : i + 2] in {"<>", "!=", "<=", ">="}:
                text = raw[i : i + 2]
                tokens.append(_Token(_TokenKind.SYMBOL, text, text))
                i += 2
                continue
            if c in "()+-*/%=<>.,":
                tokens.append(_Token(_TokenKind.SYMBOL, c, c))
                i += 1
                continue
            raise ValueError(f"Unsupported RETURN expression: {raw[i:]}")
        tokens.append(_Token(_TokenKind.EOF, "", ""))
        return tokens
