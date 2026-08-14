# Cypher2SQL Coding Policy

This project keeps Java and Python implementations aligned. Treat this file as the repo-level coding policy for future changes.

## Architecture

- Keep the Java and Python implementations at practical parity for supported behavior.
- Preserve the same conceptual layers in both languages:
  - Cypher model: parsed Cypher query parts such as query, node, edge, expression, and projection.
  - Read IR: bound read query parts such as bound pattern, bound node, and bound traversal.
  - SQL model: generated SQL query parts such as select query and join clause.
  - Schema model: graph-to-relational mapping metadata.
- Do not put unrelated responsibilities into a class just because it is nearby.
- Prefer moving behavior onto the domain object that owns the concept. For example, a pattern-related operation should live with a pattern/read-query concept, not in a detached do-er class unless a separate service object is clearly justified.

## Naming

- Keep class names as succinct as possible without losing meaning. The package name should provide context, so avoid repeating package concepts in class names.
- For example, classes under `query.sql` should prefer names such as `Grammar` and `StandardGrammar` rather than `SqlGrammar` or `StandardSqlGrammar`.
- Avoid do-er class names such as `PatternExtractor` when the behavior belongs naturally to a domain object such as `Pattern`, `Query`, or `ReadQuery`.
- Java test classes must be named `<ClassBeingTested>Test.java`.
- Not every class needs its own test class. A class can be covered by another test when it is naturally exercised through the owning public behavior.
- Use language-conventional naming:
  - Java: `UpperCamelCase` classes, `lowerCamelCase` methods and fields.
  - Python: `UpperCamelCase` classes, `snake_case` functions, methods, and fields.

## Types

- Declare types clearly in both Java and Python.
- Python targets Python 3.12 or newer. Use modern type syntax such as `str | None`, `list[str]`, and `typing.Self` where appropriate.
- Avoid weakening types to `Any` unless the boundary is genuinely dynamic, such as ANTLR parse-tree objects or deserialized schema payloads.
- Keep backward-compatible constructors or fields only when existing tests or public usage require them.

## Constants And Literals

- Do not duplicate meaningful string literals across implementation code.
- Centralize repeated messages, names, supported function maps, schema keys, and other semantic literals in one place.
- Do not centralize one-off test input strings unless it improves readability.
- `__all__` entries in Python are allowed to remain string literals because that is the module export mechanism.

## Schema Policy

- Schema metadata should describe relational facts directly; do not force users to rewrite Cypher to compensate for missing schema expressiveness.
- Cardinality is schema metadata, not query syntax. `MANY_TO_ONE` should be representable as first-class metadata even when a simple SQL join could be produced by inverting a query.
- Support both legacy scalar keys and richer list/object forms when practical, so existing schemas continue to load.
- Property-to-column mapping must be explicit when Cypher property names differ from SQL column names.
- Relationship properties, composite primary keys, composite foreign keys, qualified table names, property type/nullability, inheritance, multi-label metadata, cardinality, and uniqueness belong in the schema model.

## Tests

- Tests should assert behavior at the most useful public boundary.
- Translation tests should cover the full path from Cypher parse to SQL render when behavior depends on multiple layers.
- Keep Java and Python test coverage aligned for cross-language behavior.
- When adding unsupported placeholders, test the exact diagnostic message once and reuse centralized message construction where possible.

## Compatibility

- Preserve current read-only guardrails unless intentionally changing project scope.
- Do not implement write SQL generation as a side effect of read-query work.
- Keep unsupported features explicit with clear exceptions rather than partial or silent behavior.
