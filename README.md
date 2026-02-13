# Cypher2SQL

Translate simple Cypher graph patterns into SQL joins using a schema mapping.

## Release

### v0.1.0 (Phase 1 Complete)

Phase 1 scope is complete in this version:

- Read-only translation from Cypher `MATCH` patterns to SQL `SELECT`/`JOIN`.
- Schema mapping support for `JOIN_TABLE`, `SELF_REFERENTIAL`, and `ONE_TO_MANY`.
- Explicit multi-hop relationship chains (without `*`) translated into multiple SQL joins.
- Edge-variable projection support in `RETURN` (for example `RETURN r`) for join-table and foreign-key style mappings.
- Read-only guardrails preserved: write query classes remain placeholders for future phases.
- Foundational parser coverage for future `RETURN` function support (for example `count(*)`), with SQL translation tests intentionally deferred.

## Current Scope

- Read-only query translation is supported (`MATCH`-style graph reads to SQL `SELECT`).
- Write/update query generation is intentionally disabled for now.
  `InsertQuery`/`UpdateQuery`/`DeleteQuery` classes are placeholders reserved for future enhancements.

## Current Limitations

- No variable-length traversal support (for example `[*0..n]`).
- No write/query-mutation SQL generation (`INSERT/UPDATE/DELETE` are placeholders only).

## Cypher2SQL Roadmap

### Phase 1: Read-Only Foundation (Current)

- Parse basic `MATCH` patterns.
- Translate single-hop relationships to SQL `SELECT` + `JOIN`.
- Support schema-driven mapping kinds:
  - `JOIN_TABLE`
  - `SELF_REFERENTIAL`
  - `ONE_TO_MANY`
- Enforce read-only mode with write-query placeholders (`InsertQuery`, `UpdateQuery`, `DeleteQuery`).

### Phase 2: Read Query Expansion

- Add richer read-clause handling:
  - `WHERE` predicate translation improvements
  - `RETURN` projection mapping
  - Aggregate projection support in `RETURN` (for example `count(*)`, `count(var)`, `sum`, `avg`, `min`, `max`)
  - `ORDER BY`, `LIMIT`, `SKIP`
- Improve parser/AST normalization between Java and Python implementations.
- Add clearer diagnostics for unsupported clause combinations.

### Phase 2.1: RETURN Aggregate Functions

- Parsing:
  - Keep ANTLR-based parsing for aggregate expressions in `RETURN`.
  - Expand parse-model extraction to preserve function name, arguments, and aliases.
- Translation:
  - Translate simple aggregates without grouping (for example `RETURN count(*)`) to SQL aggregate projections.
  - Translate grouped aggregates when non-aggregate return items are present by generating `GROUP BY`.
  - Support function aliases (for example `RETURN count(*) AS total` -> `COUNT(*) AS total`).
- Validation and guardrails:
  - Reject mixed/unsupported aggregate expressions with clear error messages.
  - Keep unsupported expressions (nested functions, complex arithmetic) behind explicit errors until implemented.
- Testing:
  - Keep parser-only tests active for aggregate expressions.
  - Convert currently skipped aggregate translation tests to active once SQL translation lands.

### Phase 3: Traversal Enhancements

- Implement variable-length traversal support (for example `[*0..n]`) using recursive SQL strategies (dialect-aware).
- Add safety controls for traversal depth/cardinality.

### Phase 4: Controlled Write Support

- Replace placeholder write builders with real SQL generation for:
  - `CREATE`/`MERGE` mapping paths
  - `SET`/`REMOVE` property updates
  - `DELETE`/`DETACH DELETE` semantics (where representable)
- Add transactional and integrity safeguards.

### Phase 5: Production Hardening

- Broader dialect support and conformance tests.
- Performance profiling and query-plan optimization.
- Coverage expansion for complex Cypher constructs and edge cases.

## Cypher2SQL Roadmap & Function Support

### Roadmap: Cypher RETURN Clause Function Support

This section tracks planned support for Cypher functions in `RETURN` and `WITH` clauses, prioritized by SQL translation complexity.

### Phase 1: High Value, Low Complexity (Core Support)

- Support aggregations: `count`, `sum`, `avg`, `min`, `max`
- Support `coalesce`
- Support numeric functions: `abs`, `ceil`, `floor`, `round`, `sqrt`, `log`, `log10`, `exp`, `sin`, `cos`, `tan`, `pi`, `rand`
- Support string functions: `toUpper`, `toLower`, `trim`, `ltrim`, `rtrim`, `substring`, `replace`, `left`, `right`
- Support `CASE` expressions
- Support type conversions: `toString`, `toInteger`, `toFloat`, `toBoolean`
- Define and implement identity mapping for `id()` and `elementId()`

### Phase 2: Collection & JSON-Based Features

Requires array or JSON support in target SQL dialect.

- Support `collect()` via `array_agg` / `json_agg`
- Support list functions: `head`, `last`, `tail`, `range`, `reverse`
- Support `split()` returning array
- Support `keys()` and `properties()` (JSON-backed property model)

### Phase 3: Advanced Analytics & Statistical Functions

- Support `stDev`, `stDevP`
- Support `percentileCont`, `percentileDisc`
- Add test coverage for zero-row aggregation semantics
- Ensure parity with Cypher null-handling behavior

### Deferred: Graph-Native Semantics

- `shortestPath`
- `allShortestPaths`
- `nodes(path)`
- `relationships(path)`
- `length(path)`

These require recursive SQL or a dedicated traversal engine.

### Coverage Matrix

| Function Category | Example Functions | Phase | SQL Strategy | Difficulty |
|---|---|---|---|---|
| Basic Aggregation | `count`, `sum`, `avg`, `min`, `max` | 1 | Direct SQL aggregate | Low |
| Null Handling | `coalesce` | 1 | `COALESCE` | Low |
| Math | `abs`, `ceil`, `round`, `sqrt` | 1 | Direct mapping | Low |
| Strings | `toUpper`, `trim`, `substring` | 1 | Native SQL string functions | Low |
| CASE | `CASE WHEN` | 1 | Direct SQL `CASE` | Low |
| Type Conversion | `toInteger`, `toFloat` | 1 | `CAST` / `CONVERT` | Low |
| Identity | `id`, `elementId` | 1 | Schema-defined mapping | Medium |
| Collection Aggregate | `collect` | 2 | `array_agg` / `json_agg` | Medium |
| List Functions | `head`, `tail`, `range` | 2 | Array indexing / JSON ops | Medium |
| Map Functions | `keys`, `properties` | 2 | JSON operators | Medium |
| Statistics | `stDev`, `percentileCont` | 3 | SQL analytic functions | Medium-High |
| Path Functions | `shortestPath` | Deferred | Recursive CTE | Very High |

### SQL Dialect Strategy

Cypher2SQL should abstract over SQL dialect differences.

#### Dialect Adapter Responsibilities

Each SQL dialect adapter should define:

- Aggregate function names
- Random function name
- Array aggregation strategy
- JSON operator syntax
- Percentile function availability
- Type cast syntax

Example interface:

```text
DialectAdapter {
    renderAggregate(FunctionCall fn)
    renderStringFunction(FunctionCall fn)
    renderMathFunction(FunctionCall fn)
    renderArrayAggregation(FunctionCall fn)
    renderPercentile(FunctionCall fn)
}
```

### Semantic Parity Requirements

Before marking any function as supported:

- Null propagation must match Cypher semantics.
- Zero-row aggregation behavior must match Cypher.
- Grouping behavior must align with Cypher implicit grouping rules.
- Type coercion must be explicitly defined.
- Deterministic ordering must be documented when required.

### Contributor Guidelines For New Functions

When implementing a new Cypher function:

- Classify the function:
  - Scalar
  - Aggregate
  - List
  - Map
  - Path
  - Statistical
- Define:
  - SQL equivalent
  - Dialect differences
  - Null semantics
  - Edge-case behavior
- Add:
  - Parser support
  - AST node support
  - Translator logic
  - Dialect adapter implementation
  - Unit tests
  - Integration tests
- Add documentation entry to:
  - `README` coverage matrix
  - `CHANGELOG`

### Standard Acceptance Criteria (Per Function)

A function is complete only if:

- Correct SQL emitted for all supported dialects
- Null behavior matches Cypher
- Works inside `RETURN`
- Works inside `WITH`
- Works inside nested expressions
- Aggregation grouping behavior validated
- Unit tests cover normal, null, and edge cases
- Integration tests pass

### GitHub Issue Template

Title:

- `Support Cypher function: <function_name>`

Category:

- Scalar / Aggregate / List / Map / Path / Statistical

Phase:

- 1 / 2 / 3 / Deferred

Description:

- Brief explanation of Cypher behavior and expected SQL translation.

SQL Mapping Strategy:

- Explain mapping to SQL and dialect differences.

Null Semantics:

- Describe expected behavior with null inputs.

Edge Cases:

- Zero rows
- Mixed types
- Empty lists
- Nested usage

Acceptance Criteria:

- Parser recognizes function
- AST node implemented
- SQL translation implemented
- Dialect adapters updated
- Unit tests added
- Integration tests added
- Documentation updated

### Long-Term Architectural Goal

Cypher2SQL should:

- Support read-only Cypher as a first milestone
- Preserve Cypher semantics over naive SQL rewriting
- Be dialect-extensible
- Remain modular between parser, semantic analyzer, and SQL generator
- Avoid graph-specific features unless explicitly supported

## Clause Coverage Matrix

Legend:

- `Supported`: implemented and tested in current codebase.
- `Placeholder`: explicit stub exists, intentionally disabled.
- `Planned`: no active implementation yet.

| Cypher Clause / Feature | Status | Notes |
|---|---|---|
| `MATCH` (single-hop) | Supported | Schema-driven edge mapping to SQL joins |
| `MATCH` (multi-hop, explicit hops) | Supported | Chained relationships translate to multiple SQL joins |
| Variable-length traversal `[*m..n]` | Planned (stubbed detection) | Explicit placeholder error in mapping layer |
| `WHERE` | Limited | SQL builder has `where` support; full Cypher predicate translation not complete |
| `RETURN` | Limited | Parsing works for complete-query forms; projection translation is minimal |
| `RETURN` aggregate functions (for example `count(*)`) | Planned (parsing verified) | ANTLR parsing is covered; SQL translation tests are currently disabled/skipped |
| `ORDER BY` | Planned | Not translated yet |
| `LIMIT` / `SKIP` | Planned | Not translated yet |
| `WITH` | Planned | Not translated yet |
| `UNWIND` | Planned | Not translated yet |
| `CREATE` | Placeholder | Write mode intentionally disabled |
| `MERGE` | Placeholder | Write mode intentionally disabled |
| `SET` / `REMOVE` | Placeholder | `UpdateQuery` exists as read-only placeholder |
| `DELETE` / `DETACH DELETE` | Placeholder | `DeleteQuery` exists as read-only placeholder |

## Requirements

- Java 21 (recommended for Gradle execution)
- Python 3.12+ (or your local Python that works with dependencies)

## Schema File (`schema.yaml`)

The schema maps graph labels/types to relational tables/keys.

Top-level keys:

- `nodes`: list of node label mappings
- `edges`: list of relationship mappings

### Node Mapping

Each node needs:

- `label`: Cypher node label
- `table`: SQL table name
- `primaryKey`: primary key column in that table

Example:

```yaml
nodes:
  - label: Person
    table: people
    primaryKey: id
```

### Edge Mapping Kinds

Each edge requires:

- `type`: Cypher relationship type
- `kind`: one of `JOIN_TABLE`, `SELF_REFERENTIAL`, `ONE_TO_MANY`

#### `JOIN_TABLE`

Use for many-to-many via a join table.

Required fields:

- `fromLabel`, `toLabel`
- `joinTable`
- `fromJoinKey`, `toJoinKey`

```yaml
- type: ACTED_IN
  kind: JOIN_TABLE
  fromLabel: Person
  toLabel: Movie
  joinTable: people_movies
  fromJoinKey: person_id
  toJoinKey: movie_id
```

#### `SELF_REFERENTIAL`

Use when source and target are the same label/table.

Required fields:

- `label`
- `fromKey`, `toKey`

```yaml
- type: MANAGES
  kind: SELF_REFERENTIAL
  label: Person
  fromKey: manager_id
  toKey: id
```

#### `ONE_TO_MANY`

Use parent-child relationships where child has a foreign key to parent.

Required fields:

- `parentLabel`, `childLabel`
- `parentPrimaryKey`, `childForeignKey`

```yaml
- type: AUTHORED
  kind: ONE_TO_MANY
  parentLabel: Person
  childLabel: Movie
  parentPrimaryKey: id
  childForeignKey: author_id
```

### Full Example

See `/Users/kiisaka/IdeaProjects/Cypher2SQL/schema.example.yaml` or `/Users/kiisaka/IdeaProjects/Cypher2SQL/src/test/resources/schema.yaml`.

## Java Usage

### Build and Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

### Run Sample

Run `com.iisaka.Main` from your IDE, or add the Gradle `application` plugin if you want `./gradlew run`.

### Programmatic Example

```java
final SchemaDefinition schema = SchemaDefinitionYaml.fromPath(Path.of("schema.yaml"));
final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie)");
final String sql = new Mapping(schema).toSql(query).render(new BasicDialect());
```

## Python Usage

### Install Dev/Test Dependencies

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements-dev.txt
```

### Run Tests

```bash
PYTHONPATH=src/main/python .venv/bin/python -m unittest discover -s src/test/python/tests -v
```

### Programmatic Example

Note: the Python ANTLR parser expects a complete query form (for example, include `RETURN`).

```python
from cypher2sql.cypher_query import Query
from cypher2sql.mapping import Mapping
from cypher2sql.schema import SchemaDefinition
from cypher2sql.sql_query import BasicDialect

schema = SchemaDefinition.from_yaml_path("schema.yaml")
query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p, m")
sql = Mapping(schema).to_sql(query).render(BasicDialect())
print(sql)
```

## CI

GitHub Actions workflow is at:

- `/Users/kiisaka/IdeaProjects/Cypher2SQL/.github/workflows/ci.yml`

It runs:

- Java tests on JDK 21
- Python tests with dependencies from `requirements-dev.txt`
