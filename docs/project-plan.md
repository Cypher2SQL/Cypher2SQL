# Cypher2SQL Project Plan

Cypher2SQL is a read-only Cypher-to-SQL translator for relational schemas that model graph-shaped data. The project should stay read-only for the foreseeable future, while expanding coverage of read query clauses, expression semantics, schema-driven SQL generation, and real database execution tests.

## Product Boundary

- In scope: translating read-oriented Cypher queries into executable SQL `SELECT` statements.
- In scope: schema metadata that explains how graph labels, relationship types, properties, keys, and cardinality map to relational tables and columns.
- In scope: Java and Python parity for supported behavior.
- In scope: unit tests for parser/model/rendering behavior and integration tests that execute generated SQL against a fresh local SQL database.
- Out of scope for now: executing Cypher against Neo4j, introspecting SQL schemas from a live database, and generating SQL writes for `CREATE`, `MERGE`, `SET`, `REMOVE`, or `DELETE`.

## Source Baseline

The priority matrix is based on:

- openCypher grammar BNF: https://raw.githubusercontent.com/opencypher/openCypher/main/grammar/openCypher.bnf
- openCypher resources page: https://opencypher.org/resources/
- Neo4j current Cypher cheat sheet: https://neo4j.com/docs/cypher-manual/current/cheat-sheet/

openCypher BNF is the baseline for standard clauses. Neo4j current documentation is used to track newer clauses and extensions such as `USE`, `FILTER`, `LET`, and `SEARCH`.

## Feature Phases

### Phase 0: Stabilize Current Read Core

- Keep `MATCH`, fixed-length relationship traversal, `WHERE`, and `RETURN` reliable.
- Preserve Java/Python conceptual parity:
  - Cypher model
  - Read IR
  - SQL model
  - Schema model
- Keep generated SQL executable against the integration database.
- Maintain explicit errors for unsupported features.

### Phase 1: Complete Single-Statement Read Queries

- Add `ORDER BY`, `LIMIT`, and `SKIP`/`OFFSET` to `RETURN`.
- Complete `RETURN DISTINCT` and aggregate grouping semantics.
- Add parameter support for literals and predicates.
- Expand supported scalar, aggregate, string, numeric, and null-handling functions.
- Add SQL grammar hooks only where rendering differs across SQL engines.

### Phase 2: Clause Pipeline And Optionality

- Implement `WITH` as a first-class pipeline boundary.
- Support `WITH` projection, aliases, `DISTINCT`, aggregation, `ORDER BY`, `SKIP`, `LIMIT`, and post-`WITH` `WHERE`.
- Implement `OPTIONAL MATCH` using SQL outer join semantics.
- Support multiple `MATCH` / `OPTIONAL MATCH` clauses in one query.
- Add tests for variable scope rules, especially variables dropped by `WITH`.

### Phase 3: Multi-Row And Set Composition

- Implement `UNION` and `UNION ALL`.
- Implement `UNWIND` for list-to-row expansion where SQL support is practical.
- Add parameterized list handling for `UNWIND`.
- Add cross-language integration tests for composed read queries.

### Phase 4: Advanced Read Semantics

- Support variable-length path patterns and quantified path patterns where a recursive SQL strategy is viable.
- Support path variables and path functions such as `nodes()`, `relationships()`, and `length()` when representation is defined.
- Support subqueries and procedure-like read sources where they can be mapped safely.
- Evaluate `CALL { ... }`, `EXISTS { ... }`, and read-only `CALL ... YIELD`.

### Phase 5: Vendor And Extension Coverage

- Treat Neo4j/GQL-aligned extensions as optional, grammar-gated features.
- Evaluate `USE`, `FILTER`, `LET`, and `SEARCH`.
- Add dialect/grammar-specific feature flags before implementing engine-specific SQL.

### Deferred: Write Clauses

The grammar includes write clauses such as `CREATE`, `MERGE`, `SET`, `REMOVE`, and `DELETE`. These should remain explicit placeholders until the project intentionally expands beyond read-only translation.

## Spreadsheet

The detailed clause/action priority matrix is maintained as CSV for Git-friendly review and spreadsheet import:

- `docs/cypher-feature-priorities.csv`

Columns:

- `KeywordOrClause`: Cypher keyword or clause.
- `GrammarProduction`: Primary grammar production or documentation category.
- `Category`: Read, pipeline, expression, write, procedure, or extension.
- `ReadOnlyRelevance`: Whether it belongs in the read-only product.
- `CurrentStatus`: Current project status.
- `Priority`: `P0` highest to `P5` deferred.
- `TargetPhase`: Proposed roadmap phase.
- `ImplementationNotes`: Expected SQL strategy or project implication.
- `JavaPythonParity`: Expected parity policy.
- `Source`: Primary source URL.
