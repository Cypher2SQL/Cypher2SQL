import sqlite3
import unittest
from pathlib import Path

from cypher2sql.cypher_query import Query
from cypher2sql.mapping import Mapping
from cypher2sql.schema import SchemaDefinition
from cypher2sql.sql_query import StandardGrammar


RESOURCE_DIR = Path(__file__).parents[2] / "resources" / "integration"


class QueryTest(unittest.TestCase):
    def test_executes_rendered_sql_against_fresh_database(self) -> None:
        schema = SchemaDefinition.from_yaml_path(str(RESOURCE_DIR / "schema.yaml"))
        query = Query.parse(
            "MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) "
            "WHERE p.id = 1 AND m.id = 10 "
            "RETURN p.name, r.role, m.title"
        )
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        with sqlite3.connect(":memory:") as connection:
            connection.executescript((RESOURCE_DIR / "database.sql").read_text(encoding="utf-8"))
            rows = connection.execute(sql).fetchall()

        self.assertEqual([("Keanu Reeves", "Neo", "The Matrix")], rows)

    def test_executes_order_by_limit_and_skip_against_fresh_database(self) -> None:
        schema = SchemaDefinition.from_yaml_path(str(RESOURCE_DIR / "schema.yaml"))
        query = Query.parse("MATCH (p:Person) RETURN p.name ORDER BY p.name SKIP 1 LIMIT 1")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        with sqlite3.connect(":memory:") as connection:
            connection.executescript((RESOURCE_DIR / "database.sql").read_text(encoding="utf-8"))
            rows = connection.execute(sql).fetchall()

        self.assertEqual([("Keanu Reeves",)], rows)

    def test_executes_optional_match_as_outer_join_against_fresh_database(self) -> None:
        schema = SchemaDefinition.from_yaml_path(str(RESOURCE_DIR / "schema.yaml"))
        query = Query.parse(
            "MATCH (p:Person) OPTIONAL MATCH (p)-[:DIRECTED]->(m:Movie) RETURN p.name, m.title ORDER BY p.name"
        )
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        with sqlite3.connect(":memory:") as connection:
            connection.executescript((RESOURCE_DIR / "database.sql").read_text(encoding="utf-8"))
            rows = connection.execute(sql).fetchall()

        self.assertEqual(
            [
                ("Carrie-Anne Moss", None),
                ("Keanu Reeves", "Speed"),
                ("Lana Wachowski", "The Matrix"),
            ],
            rows,
        )


if __name__ == "__main__":
    unittest.main()
