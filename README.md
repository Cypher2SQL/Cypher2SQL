# Cypher2SQL

Translate simple Cypher graph patterns into SQL joins using a schema mapping.

## Current Scope

- Read-only query translation is supported (`MATCH`-style graph reads to SQL `SELECT`).
- Write/update query generation is intentionally disabled for now.
  `InsertQuery`/`UpdateQuery`/`DeleteQuery` classes are placeholders reserved for future enhancements.

## Current Limitations

- No variable-length traversal support (for example `[*0..n]`).
- No multi-hop traversal planning in a single query pattern (for example chained relationship hops).
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
  - `ORDER BY`, `LIMIT`, `SKIP`
- Improve parser/AST normalization between Java and Python implementations.
- Add clearer diagnostics for unsupported clause combinations.

### Phase 3: Traversal Enhancements

- Implement multi-hop traversal planning.
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

## Clause Coverage Matrix

Legend:

- `Supported`: implemented and tested in current codebase.
- `Placeholder`: explicit stub exists, intentionally disabled.
- `Planned`: no active implementation yet.

| Cypher Clause / Feature | Status | Notes |
|---|---|---|
| `MATCH` (single-hop) | Supported | Schema-driven edge mapping to SQL joins |
| `MATCH` (multi-hop) | Planned (stubbed detection) | Explicit placeholder error in mapping layer |
| Variable-length traversal `[*m..n]` | Planned (stubbed detection) | Explicit placeholder error in mapping layer |
| `WHERE` | Limited | SQL builder has `where` support; full Cypher predicate translation not complete |
| `RETURN` | Limited | Parsing works for complete-query forms; projection translation is minimal |
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
final Query query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie)");
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
query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p, m")
sql = Mapping(schema).to_sql(query).render(BasicDialect())
print(sql)
```

## CI

GitHub Actions workflow is at:

- `/Users/kiisaka/IdeaProjects/Cypher2SQL/.github/workflows/ci.yml`

It runs:

- Java tests on JDK 21
- Python tests with dependencies from `requirements-dev.txt`
