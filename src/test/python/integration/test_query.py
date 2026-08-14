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


if __name__ == "__main__":
    unittest.main()
