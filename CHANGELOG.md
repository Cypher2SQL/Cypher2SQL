# Changelog

All notable changes to this project are documented in this file.

## Unreleased

### Added
- `ORDER BY` translation for `RETURN`, including multiple sort keys and explicit `ASC`/`DESC` (Java and Python).
- `LIMIT` and `SKIP` translation for `RETURN`, restricted to integer literals; `SKIP` without `LIMIT` renders as `LIMIT -1 OFFSET n` for SQLite/Postgres/MySQL compatibility (Java and Python).
- `OPTIONAL MATCH` translation to SQL `LEFT JOIN`.
- `RETURN DISTINCT` / `WITH DISTINCT`, mapping to `SELECT DISTINCT`.
- `WHERE` directly on an `OPTIONAL MATCH` clause, folded into the `LEFT JOIN`'s `ON` condition (not a global `WHERE`) so a failing predicate null-extends the row instead of dropping it or silently degrading to an `INNER JOIN`.
- `WITH` as a pipeline boundary: renders as a derived-table subquery that the following `RETURN`/`WHERE`/`ORDER BY`/`LIMIT`/`SKIP`/`DISTINCT` runs against. Scoped to one `WITH` per query, no `MATCH` after it, no aggregation grouping (mixed aggregate/non-aggregate items — no `GROUP BY` support exists anywhere yet), and at most one node variable passed through unchanged (its columns are selected as `alias.*`, so more than one risks colliding/duplicate column names across differently-shaped tables); a bare relationship-variable passthrough is also unsupported. All violations are explicit errors, not silently wrong SQL.

### Fixed
- Integer literals in Cypher expressions (Java) were always parsed as `Double` due to Java ternary-operator numeric promotion in the literal parser, silently losing integer-ness even though rendering happened to mask it. Numeric literal parsing now returns `Long` for integers as intended.
- Node-alias allocation (`t0`, `t1`, ...) is now a single counter shared across all patterns in a query instead of restarting per pattern, fixing a latent alias-collision bug that would have affected any future multi-pattern query.

### Changed
- SQL rendering (`WHERE`/`RETURN`/`ORDER BY` expression resolution) moved from `BoundPattern` to `ReadQuery` in both languages, since it now needs to resolve variables across every pattern in a query, not just one.

## v0.1.0 - Phase 1 Complete (February 13, 2026)

### Added
- Read-only Cypher `MATCH` to SQL `SELECT`/`JOIN` translation foundation.
- Schema mapping strategies: `JOIN_TABLE`, `SELF_REFERENTIAL`, `ONE_TO_MANY`.
- Explicit multi-hop relationship translation (non-variable-length traversals).
- Edge-variable projection in `RETURN` (for example `RETURN r`) for:
  - Join-table relationships (join-table row projection).
  - Foreign-key relationships (relevant key-column projection).
- Anonymous-node pattern support (for example `MATCH ()-[r:TYPE]->()`).
- Expanded parser and integration coverage across Java and Python.
- Parser-only tests for future function support in `RETURN` (for example `count(*)`).

### Changed
- Project is explicitly marked read-only for writes in this phase.
- Naming and architecture aligned to reduce redundant class names and centralize cohesive behavior.

### Deferred
- Cypher `WHERE` predicate translation semantics.
- Cypher function SQL translation in `RETURN`/`WITH` (beyond parser recognition).
- Variable-length traversal support (`[*m..n]`).
- Write query SQL generation (`INSERT`, `UPDATE`, `DELETE`).
