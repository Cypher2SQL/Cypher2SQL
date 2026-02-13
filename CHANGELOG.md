# Changelog

All notable changes to this project are documented in this file.

## v0.1.0 - Phase 1 Complete

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
