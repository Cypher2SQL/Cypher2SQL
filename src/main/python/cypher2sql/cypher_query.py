from __future__ import annotations

import builtins
from dataclasses import dataclass
from enum import Enum
from typing import Any

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


@dataclass(frozen=True)
class Expression:
    pass


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


class Query:
    def __init__(
        self,
        raw: str,
        patterns: list[Pattern],
        parse_tree: Any,
        return_items: list[ReturnItem | ProjectionItem] | None = None,
        where_expression: Expression | None = None,
        with_projection_items: list[ProjectionItem] | None = None,
        with_where_expression: Expression | None = None,
        has_variable_length_traversal: bool | None = None,
    ) -> None:
        self._raw = raw
        self._patterns = list(patterns)
        self._parse_tree = parse_tree
        self._return_items = [
            item if isinstance(item, ProjectionItem) else ProjectionItem(item.expression, item.alias)
            for item in ([] if return_items is None else list(return_items))
        ]
        self._where_expression = where_expression
        self._with_projection_items = [] if with_projection_items is None else list(with_projection_items)
        self._with_where_expression = with_where_expression
        self._has_variable_length_traversal = (
            ("[*" in raw) if has_variable_length_traversal is None else has_variable_length_traversal
        )

    @property
    def raw(self) -> str:
        return self._raw

    @property
    def patterns(self) -> list[Pattern]:
        return list(self._patterns)

    @property
    def parse_tree(self) -> Any:
        return self._parse_tree

    @property
    def return_items(self) -> list[ProjectionItem]:
        return list(self._return_items)

    @property
    def projection_items(self) -> list[ProjectionItem]:
        return list(self._return_items)

    @property
    def where_expression(self) -> Expression | None:
        return self._where_expression

    @property
    def with_projection_items(self) -> list[ProjectionItem]:
        return list(self._with_projection_items)

    @property
    def with_where_expression(self) -> Expression | None:
        return self._with_where_expression

    @property
    def has_with_clause(self) -> bool:
        return bool(self._with_projection_items) or self._with_where_expression is not None

    @property
    def has_variable_length_traversal(self) -> bool:
        return self._has_variable_length_traversal

    @classmethod
    def parse(cls, cypher: str) -> "Query":
        parse_tree, parser = _parse(cypher)
        patterns = _extract_patterns(parser, parse_tree)
        return cls(
            cypher,
            patterns,
            parse_tree,
            _extract_projection_items(parser, parse_tree, in_with=False),
            _extract_where_expression(parser, parse_tree, in_with=False),
            _extract_projection_items(parser, parse_tree, in_with=True),
            _extract_where_expression(parser, parse_tree, in_with=True),
            _has_variable_length_traversal(parser, parse_tree),
        )


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


def _extract_projection_items(parser: Any, parse_tree: Any, in_with: bool) -> list[ProjectionItem]:
    return_items: list[ProjectionItem] = []
    stack: list[Any] = [parse_tree]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name is not None and _is_projection_item_rule(rule_name) and _is_with_context(current) == in_with:
            expr, alias = _projection_expression_and_alias(current)
            return_items.append(ProjectionItem(parse_expression(expr), alias))
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return return_items


def _extract_where_expression(parser: Any, parse_tree: Any, in_with: bool) -> Expression | None:
    stack: list[Any] = [parse_tree]
    while stack:
        current = stack.pop()
        rule_name = _rule_name(parser, current)
        if rule_name is not None and _normalized_rule_name(rule_name) == "where" and _is_with_context(current) == in_with:
            for idx in range(getattr(current, "getChildCount", lambda: 0)()):
                child = current.getChild(idx)
                if _rule_name(parser, child) == "expression":
                    return parse_expression(child.getText())
            text = current.getText()
            return parse_expression(text[5:] if text.upper().startswith("WHERE") else text)
        child_count = getattr(current, "getChildCount", lambda: 0)()
        for idx in range(child_count - 1, -1, -1):
            stack.append(current.getChild(idx))
    return None


def _extract_patterns(parser: Any, parse_tree: Any) -> list[Pattern]:
    roots = _find_pattern_elements(parser, parse_tree)
    if not roots:
        return []

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
    return patterns


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


def _is_with_context(context: Any) -> bool:
    current = getattr(context, "parentCtx", None) or getattr(context, "parent", None)
    while current is not None:
        name = type(current).__name__.lower()
        text = getattr(current, "getText", lambda: "")()
        if "with" in name or text.upper().startswith("WITH"):
            return True
        current = getattr(current, "parentCtx", None) or getattr(current, "parent", None)
    return False


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


def _is_identifier(value: str) -> bool:
    if not value:
        return False
    if not (value[0].isalpha() or value[0] == "_"):
        return False
    for c in value[1:]:
        if not (c.isalnum() or c == "_"):
            return False
    return True
