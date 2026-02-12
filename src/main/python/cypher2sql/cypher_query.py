from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import re
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
class CypherNode:
    variable: str
    label: str | None


@dataclass(frozen=True)
class CypherEdge:
    variable: str | None
    type: str | None
    direction: Direction


@dataclass(frozen=True)
class CypherPattern:
    nodes: List[CypherNode]
    edges: List[CypherEdge]


@dataclass(frozen=True)
class CypherReturnItem:
    variable: str
    property: str | None = None


class CypherQuery:
    _simple_pattern = re.compile(
        r"\((?P<left_var>\w+)(?::(?P<left_label>\w+))?\)\s*-\s*"
        r"\[(?P<edge_var>\w+)?(?::(?P<edge_type>\w+))?\]\s*-"
        r"(?P<dir>>|<)?\s*\((?P<right_var>\w+)(?::(?P<right_label>\w+))?\)"
    )

    def __init__(
        self,
        raw: str,
        patterns: List[CypherPattern],
        parse_tree: Any,
        return_items: List[CypherReturnItem] | None = None,
    ) -> None:
        self._raw = raw
        self._patterns = list(patterns)
        self._parse_tree = parse_tree
        self._return_items = [] if return_items is None else list(return_items)

    @property
    def raw(self) -> str:
        return self._raw

    @property
    def patterns(self) -> List[CypherPattern]:
        return list(self._patterns)

    @property
    def parse_tree(self) -> Any:
        return self._parse_tree

    @property
    def return_items(self) -> List[CypherReturnItem]:
        return list(self._return_items)

    @classmethod
    def parse(cls, cypher: str) -> "CypherQuery":
        parse_tree, parser = _parse(cypher)
        match = cls._simple_pattern.search(cypher)
        patterns: List[CypherPattern] = []
        if match:
            left = CypherNode(match.group("left_var"), match.group("left_label"))
            right = CypherNode(match.group("right_var"), match.group("right_label"))
            direction = Direction.UNDIRECTED
            if match.group("dir") == ">":
                direction = Direction.LEFT_TO_RIGHT
            elif match.group("dir") == "<":
                direction = Direction.RIGHT_TO_LEFT
            edge = CypherEdge(
                match.group("edge_var"),
                match.group("edge_type"),
                direction,
            )
            patterns.append(CypherPattern([left, right], [edge]))
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


def _extract_return_items(parser: Any, parse_tree: Any) -> List[CypherReturnItem]:
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


def _parse_projection_expression(expr: str) -> CypherReturnItem:
    dot = expr.find(".")
    if dot < 0:
        if _is_identifier(expr):
            return CypherReturnItem(expr)
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
        return CypherReturnItem(variable, prop)
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
