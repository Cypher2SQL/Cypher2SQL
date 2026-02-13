from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, List

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
    variable: str
    label: str | None


@dataclass(frozen=True)
class Edge:
    variable: str | None
    type: str | None
    direction: Direction


@dataclass(frozen=True)
class Pattern:
    nodes: List[Node]
    edges: List[Edge]


@dataclass(frozen=True)
class ReturnItem:
    variable: str
    property: str | None = None


class Query:
    def __init__(
        self,
        raw: str,
        patterns: List[Pattern],
        parse_tree: Any,
        return_items: List[ReturnItem] | None = None,
    ) -> None:
        self._raw = raw
        self._patterns = list(patterns)
        self._parse_tree = parse_tree
        self._return_items = [] if return_items is None else list(return_items)

    @property
    def raw(self) -> str:
        return self._raw

    @property
    def patterns(self) -> List[Pattern]:
        return list(self._patterns)

    @property
    def parse_tree(self) -> Any:
        return self._parse_tree

    @property
    def return_items(self) -> List[ReturnItem]:
        return list(self._return_items)

    @classmethod
    def parse(cls, cypher: str) -> "Query":
        parse_tree, parser = _parse(cypher)
        patterns = _extract_patterns(parser, parse_tree)
        return cls(cypher, patterns, parse_tree, _extract_return_items(parser, parse_tree))


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

    for rule in ("oC_Cypher", "cypher", "statement", "query"):
        rule_fn = getattr(parser, rule, None)
        if rule_fn is not None:
            return rule_fn(), parser
    raise RuntimeError("No supported Cypher entry rule found on parser.")


def _extract_return_items(parser: Any, parse_tree: Any) -> List[ReturnItem]:
    return_items = []
    stack: List[Any] = [parse_tree]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name is not None and _is_return_item_rule(rule_name):
            expr = _return_expression_text(current)
            return_items.append(_parse_projection_expression(expr))
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return return_items


def _extract_patterns(parser: Any, parse_tree: Any) -> List[Pattern]:
    pattern_root = _find_first_pattern_element(parser, parse_tree)
    if pattern_root is None:
        return []

    node_contexts: List[Any] = []
    relationship_contexts: List[Any] = []
    stack: List[Any] = [pattern_root]
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
        return []

    nodes = [_parse_node_text(ctx.getText()) for ctx in node_contexts]
    edges = [_parse_edge_text(ctx.getText()) for ctx in relationship_contexts]
    return [Pattern(nodes=nodes, edges=edges)]


def _find_first_pattern_element(parser: Any, parse_tree: Any) -> Any | None:
    stack: List[Any] = [parse_tree]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name in {"patternElement", "patternPart"}:
            return current
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return None


def _rule_name(parser: Any, context: Any) -> str | None:
    get_rule_index = getattr(context, "getRuleIndex", None)
    if get_rule_index is None:
        return None
    return parser.ruleNames[get_rule_index()]


def _is_return_item_rule(rule_name: str) -> bool:
    normalized = "".join(c for c in rule_name if c.isalnum()).lower()
    return normalized.endswith("returnitem") or normalized.endswith("projectionitem")


def _return_expression_text(context: Any) -> str:
    before_alias: List[str] = []
    saw_as = False
    for i in range(context.getChildCount()):
        child = context.getChild(i)
        if isinstance(child, TerminalNode) and child.getText().upper() == "AS":
            saw_as = True
            break
        before_alias.append(child.getText())
    return ("".join(before_alias) if saw_as else context.getText()).strip()


def _parse_projection_expression(expr: str) -> ReturnItem:
    dot = expr.find(".")
    if dot < 0:
        if _is_identifier(expr):
            return ReturnItem(expr)
        raise ValueError(
            f"Unsupported RETURN expression: {expr}. Only variable or variable.property are supported."
        )
    if expr.find(".", dot + 1) >= 0:
        raise ValueError(
            f"Unsupported RETURN expression: {expr}. Only variable or variable.property are supported."
        )
    variable = expr[:dot]
    prop = expr[dot + 1 :]
    if _is_identifier(variable) and _is_identifier(prop):
        return ReturnItem(variable, prop)
    raise ValueError(
        f"Unsupported RETURN expression: {expr}. Only variable or variable.property are supported."
    )


def _is_identifier(value: str) -> bool:
    if not value:
        return False
    if not (value[0].isalpha() or value[0] == "_"):
        return False
    for c in value[1:]:
        if not (c.isalnum() or c == "_"):
            return False
    return True


def _parse_node_text(text: str) -> Node:
    open_idx = text.find("(")
    close_idx = text.rfind(")")
    if open_idx < 0 or close_idx < open_idx:
        raise ValueError(f"Unsupported node pattern: {text}")

    inside = text[open_idx + 1 : close_idx]
    properties_at = inside.find("{")
    if properties_at >= 0:
        inside = inside[:properties_at]
    where_at = inside.upper().find("WHERE")
    if where_at >= 0:
        inside = inside[:where_at]
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
