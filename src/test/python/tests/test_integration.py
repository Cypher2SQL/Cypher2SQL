import unittest

from cypher2sql.cypher_query import Direction, Query, _parse
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

    def test_parse_and_render_with_anonymous_left_node(self) -> None:
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
        query = Query.parse("MATCH (:Person)-[r:ACTED_IN]->(m:Movie) RETURN m")
        sql = Mapping(schema).to_sql(query).render(BasicDialect())

        self.assertEqual(
            "SELECT t1.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_single_node_match(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p")
        sql = Mapping(schema).to_sql(query).render(BasicDialect())

        self.assertEqual("SELECT t0.* FROM \"people\" t0", sql)

    def test_parses_anonymous_nodes_around_relationship(self) -> None:
        query = Query.parse("MATCH ()-[r:ACTED_IN]->() RETURN r")

        patterns = query.patterns
        self.assertEqual(1, len(patterns))
        self.assertEqual(2, len(patterns[0].nodes))
        self.assertIsNone(patterns[0].nodes[0].variable)
        self.assertIsNone(patterns[0].nodes[1].variable)
        self.assertEqual(1, len(patterns[0].edges))
        self.assertEqual("r", patterns[0].edges[0].variable)
        self.assertEqual("ACTED_IN", patterns[0].edges[0].type)
        self.assertEqual(Direction.LEFT_TO_RIGHT, patterns[0].edges[0].direction)

    def test_antlr_parses_count_star_return_expression(self) -> None:
        parse_tree, _parser = _parse("MATCH (p:Person) RETURN count(*)")
        text = parse_tree.getText().replace(" ", "").lower()
        self.assertIn("match", text)
        self.assertIn("return", text)
        self.assertIn("count(*)", text)

    def test_parse_and_render_edge_variable_with_anonymous_nodes(self) -> None:
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
        query = Query.parse("MATCH ()-[r:ACTED_IN]->() RETURN r")
        sql = Mapping(schema).to_sql(query).render(BasicDialect())

        self.assertEqual(
            "SELECT j2.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_edge_variable_with_anonymous_nodes_without_join_table(self) -> None:
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
            kind: ONE_TO_MANY
            parentLabel: Person
            childLabel: Movie
            parentPrimaryKey: id
            childForeignKey: author_id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH ()-[r:ACTED_IN]->() RETURN r")
        sql = Mapping(schema).to_sql(query).render(BasicDialect())

        self.assertEqual(
            "SELECT t1.author_id, t0.id FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
            sql,
        )

    def test_parse_and_render_self_referential_edge_variable_with_anonymous_nodes(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges:
          - type: MANAGES
            kind: SELF_REFERENTIAL
            label: Person
            fromKey: manager_id
            toKey: id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH ()-[r:MANAGES]->() RETURN r")
        sql = Mapping(schema).to_sql(query).render(BasicDialect())

        self.assertEqual(
            "SELECT t0.manager_id, t1.id FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
            sql,
        )

    @unittest.skip("Enable when RETURN function projection support (e.g. count(*)) is implemented.")
    def test_parse_and_render_count_star_projection(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(*)")
        sql = Mapping(schema).to_sql(query).render(BasicDialect())

        self.assertEqual(
            "SELECT COUNT(*) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )


if __name__ == "__main__":
    unittest.main()
