import unittest

from cypher2sql import cypher_query
from cypher2sql.cypher_query import Direction, Query, _parse
from cypher2sql.mapping import Mapping
from cypher2sql.schema import SchemaDefinition
from cypher2sql.sql_query import StandardGrammar


class IntegrationTest(unittest.TestCase):
    def setUp(self) -> None:
        if cypher_query.InputStream is None:
            self.skipTest("ANTLR runtime not available. Install antlr4-python3-runtime and antlr4-cypher.")

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
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

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
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

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
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

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
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

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
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

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
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT t0.manager_id, t1.id FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
            sql,
        )

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
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT COUNT(*) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_count_node_projection(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(p)")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT COUNT(t0.id) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_where_predicate(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE p.id > 1 AND p.id < 10 RETURN p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 WHERE ((t0.id > 1) AND (t0.id < 10))", sql)

    def test_parse_and_render_arithmetic_return_expression_with_alias(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id + m.id AS total")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT (t0.id + t1.id) AS total FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_comparison_and_logical_return_expression(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id > 1 AND m.id < 10 AS matches")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT ((t0.id > 1) AND (t1.id < 10)) AS matches FROM \"people\" t0 "
            "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_case_return_expression(self) -> None:
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
        query = Query.parse(
            "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN CASE WHEN p.id > 1 THEN m.id ELSE 0 END AS score"
        )
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT CASE WHEN (t0.id > 1) THEN t1.id ELSE 0 END AS score FROM \"people\" t0 "
            "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_function_return_expression_without_alias(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN abs(p.id)")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT ABS(t0.id) FROM \"people\" t0", sql)

    def test_parse_unsupported_function_but_fails_at_sql_rendering(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN mystery(p.id)")

        with self.assertRaisesRegex(NotImplementedError, "Function is parsed but not rendered yet: mystery"):
            Mapping(schema).to_sql(query)

    def test_parse_with_clause_placeholder(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WITH p.id AS pid WHERE p.id > 1 RETURN pid")

        self.assertTrue(query.has_with_clause)
        self.assertEqual("pid", query.with_projection_items[0].alias)
        with self.assertRaisesRegex(
            NotImplementedError,
            "WITH clauses are parsed but not rendered yet; pipeline semantics are a future enhancement.",
        ):
            Mapping(schema).to_sql(query)

    def test_parse_and_render_mapped_properties_qualified_tables_and_composite_keys(self) -> None:
        raw = """
        nodes:
          - label: Person
            schema: graph
            table: people
            primaryKeys: [tenant_id, id]
            properties:
              name: full_name
          - label: Movie
            schema: graph
            table: movies
            primaryKeys: [tenant_id, id]
            properties:
              title: movie_title
        edges:
          - type: ACTED_IN
            kind: JOIN_TABLE
            fromLabel: Person
            toLabel: Movie
            joinTable: graph.people_movies
            fromJoinKeys: [person_tenant_id, person_id]
            toJoinKeys: [movie_tenant_id, movie_id]
            properties:
              role: character_name
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) RETURN p.name, r.role, m.title")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT t0.full_name, j2.character_name, t1.movie_title FROM \"graph\".\"people\" t0 "
            "INNER JOIN \"graph\".\"people_movies\" j2 ON t0.tenant_id = j2.person_tenant_id AND t0.id = j2.person_id "
            "INNER JOIN \"graph\".\"movies\" t1 ON j2.movie_tenant_id = t1.tenant_id AND j2.movie_id = t1.id",
            sql,
        )

if __name__ == "__main__":
    unittest.main()
