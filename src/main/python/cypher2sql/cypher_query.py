from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import re
from typing import Any, List

try:
    from antlr4 import InputStream, CommonTokenStream
    from antlr4.error.ErrorListener import ErrorListener
    from antlr4_cypher import CypherLexer, CypherParser
except ImportError as exc:  # pragma: no cover - runtime dependency
    InputStream = None
    CommonTokenStream = None
    ErrorListener = object
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


class CypherQuery:
    _simple_pattern = re.compile(
        r"\((?P<left_var>\w+)(?::(?P<left_label>\w+))?\)\s*-\s*"
        r"\[(?P<edge_var>\w+)?(?::(?P<edge_type>\w+))?\]\s*-"
        r"(?P<dir>>|<)?\s*\((?P<right_var>\w+)(?::(?P<right_label>\w+))?\)"
    )

    def __init__(self, raw: str, patterns: List[CypherPattern], parse_tree: Any) -> None:
        self._raw = raw
        self._patterns = list(patterns)
        self._parse_tree = parse_tree

    @property
    def raw(self) -> str:
        return self._raw

    @property
    def patterns(self) -> List[CypherPattern]:
        return list(self._patterns)

    @property
    def parse_tree(self) -> Any:
        return self._parse_tree

    @classmethod
    def parse(cls, cypher: str) -> "CypherQuery":
        parse_tree = _parse_tree(cypher)
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
        return cls(cypher, patterns, parse_tree)


class _CypherSyntaxErrorListener(ErrorListener):
    def syntaxError(self, recognizer, offendingSymbol, line, column, msg, e):  # noqa: N802
        raise ValueError(f"Cypher syntax error at line {line}, column {column}: {msg}") from e


def _parse_tree(cypher: str) -> Any:
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
            return rule_fn()
    raise RuntimeError("No supported Cypher entry rule found on parser.")
