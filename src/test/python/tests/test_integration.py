import unittest

from cypher2sql.cypher_query import Query
from cypher2sql.mapping import Mapping
from cypher2sql.schema import SchemaDefinition
from cypher2sql.sql_query import BasicDialect

class IntegrationTest(unittest.TestCase):
    def test_parse_and_render(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
          - label: Movie
            table: movies
            primaryKey: id
        edges:
          - type: ACTED_IN
            kind: JOIN_TABLE
            fromLabel: Person
            toLabel: Movie
            joinTable: people_movies
            fromJoinKey: person_id
            toJoinKey: movie_id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) RETURN p, m")
        sql = Mapping(schema).to_sql(query).render(BasicDialect())

        self.assertIsNotNone(query.parse_tree)
        self.assertEqual(
            "SELECT t0.*, t1.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )


if __name__ == "__main__":
    unittest.main()
