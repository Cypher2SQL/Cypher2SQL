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

        patterns = query.match_clauses[0].patterns
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

    def test_parse_and_render_subtraction_return_expression(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id - m.id")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT (t0.id - t1.id) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_multiplication_return_expression(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id * m.id")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT (t0.id * t1.id) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_division_return_expression(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id / m.id")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT (t0.id / t1.id) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_modulo_return_expression(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id % m.id")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT (t0.id % t1.id) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_unary_negate_return_expression(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN -p.id")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT (- t0.id) FROM \"people\" t0", sql)

    def test_parse_and_render_not_equals_comparison(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE p.id <> 1 RETURN p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 WHERE (t0.id <> 1)", sql)

    def test_parse_and_render_less_than_or_equal_comparison(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE p.id <= 1 RETURN p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 WHERE (t0.id <= 1)", sql)

    def test_parse_and_render_greater_than_or_equal_comparison(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE p.id >= 1 RETURN p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 WHERE (t0.id >= 1)", sql)

    def test_parse_and_render_or_logical_expression(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE p.id > 1 OR p.id < 10 RETURN p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 WHERE ((t0.id > 1) OR (t0.id < 10))", sql)

    def test_parse_and_render_not_logical_expression(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE NOT p.id > 1 RETURN p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 WHERE (NOT (t0.id > 1))", sql)

    def test_parse_and_render_sum_aggregate_projection(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN sum(p.id)")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT SUM(t0.id) FROM \"people\" t0", sql)

    def test_parse_and_render_avg_aggregate_projection(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN avg(p.id)")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT AVG(t0.id) FROM \"people\" t0", sql)

    def test_parse_and_render_min_aggregate_projection(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN min(p.id)")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT MIN(t0.id) FROM \"people\" t0", sql)

    def test_parse_and_render_max_aggregate_projection(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN max(p.id)")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT MAX(t0.id) FROM \"people\" t0", sql)

    def test_parse_and_render_string_literal_constant(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE p.name = 'Alice' RETURN p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 WHERE (t0.name = 'Alice')", sql)

    def test_parse_and_render_boolean_literal_constants(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)

        true_query = Query.parse("MATCH (p:Person) RETURN true")
        false_query = Query.parse("MATCH (p:Person) RETURN false")

        self.assertEqual(
            "SELECT TRUE FROM \"people\" t0", Mapping(schema).to_sql(true_query).render(StandardGrammar())
        )
        self.assertEqual(
            "SELECT FALSE FROM \"people\" t0", Mapping(schema).to_sql(false_query).render(StandardGrammar())
        )

    def test_parse_and_render_null_literal_constant(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN null")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT NULL FROM \"people\" t0", sql)

    def test_parse_and_render_non_integral_float_literal(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN 1.5")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT 1.5 FROM \"people\" t0", sql)

    def test_parse_and_render_integral_float_literal_without_trailing_zero(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN 2.0")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT 2 FROM \"people\" t0", sql)

    def test_parse_and_render_simple_case_with_subject(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN CASE p.id WHEN 1 THEN 'one' ELSE 'other' END AS label")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT CASE t0.id WHEN 1 THEN 'one' ELSE 'other' END AS label FROM \"people\" t0", sql)

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

    def test_parse_and_render_with_aliased_scalar_projection(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WITH p.id AS pid WHERE pid > 1 RETURN pid")

        self.assertTrue(query.has_with_clause)
        self.assertEqual("pid", query.with_clause.items[0].alias)
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT with0.pid FROM (SELECT t0.id AS pid FROM \"people\" t0) with0 WHERE (with0.pid > 1)",
            sql,
        )

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

    def test_parse_and_render_order_by_ascending_by_default(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p ORDER BY p.id")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 ORDER BY t0.id ASC", sql)

    def test_parse_and_render_order_by_descending(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p ORDER BY p.id DESC")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 ORDER BY t0.id DESC", sql)

    def test_parse_and_render_order_by_multiple_columns_with_mixed_direction(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p ORDER BY p.name ASC, p.id DESC")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 ORDER BY t0.name ASC, t0.id DESC", sql)

    def test_parse_and_render_order_by_on_bare_variable(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p ORDER BY p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 ORDER BY t0.id ASC", sql)

    def test_parse_and_render_limit(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p LIMIT 5")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 LIMIT 5", sql)

    def test_parse_and_render_skip_as_unlimited_offset(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p SKIP 5")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 LIMIT -1 OFFSET 5", sql)

    def test_parse_and_render_limit_with_skip(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN p SKIP 2 LIMIT 5")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT t0.* FROM \"people\" t0 LIMIT 5 OFFSET 2", sql)

    def test_parse_raises_for_non_literal_limit(self) -> None:
        with self.assertRaises(NotImplementedError) as context:
            Query.parse("MATCH (p:Person) RETURN p LIMIT p.id")

        self.assertEqual("LIMIT must be an integer literal; parameters are not supported yet.", str(context.exception))

    def test_parse_raises_for_non_literal_skip(self) -> None:
        with self.assertRaises(NotImplementedError) as context:
            Query.parse("MATCH (p:Person) RETURN p SKIP p.id")

        self.assertEqual("SKIP must be an integer literal; parameters are not supported yet.", str(context.exception))

    def test_parse_and_render_optional_match_as_left_join(self) -> None:
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
          - type: AUTHORED
            kind: ONE_TO_MANY
            parentLabel: Person
            childLabel: Movie
            parentPrimaryKey: id
            childForeignKey: author_id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse(
            "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (m)<-[:AUTHORED]-(a:Person) RETURN p"
        )
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people_movies\" j3 ON t0.id = j3.person_id "
            "INNER JOIN \"movies\" t1 ON j3.movie_id = t1.id "
            "LEFT JOIN \"people\" t2 ON t1.author_id = t2.id",
            sql,
        )

    def test_parse_and_render_variable_bound_only_by_optional_match(self) -> None:
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
          - type: AUTHORED
            kind: ONE_TO_MANY
            parentLabel: Person
            childLabel: Movie
            parentPrimaryKey: id
            childForeignKey: author_id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse(
            "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (m)<-[:AUTHORED]-(a:Person) RETURN a"
        )
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT t2.* FROM \"people\" t0 INNER JOIN \"people_movies\" j3 ON t0.id = j3.person_id "
            "INNER JOIN \"movies\" t1 ON j3.movie_id = t1.id "
            "LEFT JOIN \"people\" t2 ON t1.author_id = t2.id",
            sql,
        )

    def test_parse_raises_for_optional_match_on_unbound_variable(self) -> None:
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
          - type: AUTHORED
            kind: ONE_TO_MANY
            parentLabel: Person
            childLabel: Movie
            parentPrimaryKey: id
            childForeignKey: author_id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse(
            "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (a:Person)-[:AUTHORED]->(other:Movie) RETURN p"
        )

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual(
            "OPTIONAL MATCH must reference a variable already bound by a preceding MATCH clause.",
            str(context.exception),
        )

    def test_parse_raises_for_leading_optional_match(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("OPTIONAL MATCH (p:Person) RETURN p")

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual("OPTIONAL MATCH cannot be the first clause yet.", str(context.exception))

    def test_parse_and_render_where_on_optional_match_as_extra_join_condition(self) -> None:
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
          - type: AUTHORED
            kind: ONE_TO_MANY
            parentLabel: Person
            childLabel: Movie
            parentPrimaryKey: id
            childForeignKey: author_id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse(
            "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (m)<-[:AUTHORED]-(a:Person) "
            "WHERE a.id > 1 RETURN p"
        )
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people_movies\" j3 ON t0.id = j3.person_id "
            "INNER JOIN \"movies\" t1 ON j3.movie_id = t1.id "
            "LEFT JOIN \"people\" t2 ON t1.author_id = t2.id AND ((t2.id > 1))",
            sql,
        )

    def test_parse_raises_for_two_non_optional_match_clauses(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
          - label: Movie
            table: movies
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) MATCH (m:Movie) RETURN p, m")

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual(
            "Multiple top-level MATCH patterns are not supported yet. Found: 2", str(context.exception)
        )

    def test_parse_and_render_return_distinct_on_property(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN DISTINCT p.name")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT DISTINCT t0.name FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
            sql,
        )

    def test_parse_and_render_return_distinct_on_wildcard(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) RETURN DISTINCT p")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT DISTINCT t0.* FROM \"people\" t0", sql)

    def test_parse_and_render_with_bare_node_passthrough(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WITH p RETURN p.name")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual("SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0) with0", sql)

    def test_parse_and_render_with_bare_node_passthrough_and_pre_with_where(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WHERE p.id > 1 WITH p RETURN p.name")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0 WHERE (t0.id > 1)) with0",
            sql,
        )

    def test_parse_and_render_with_whole_query_aggregate_without_grouping(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH count(m) AS total RETURN total")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT with0.total FROM (SELECT COUNT(t1.id) AS total FROM \"people\" t0 "
            "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id) with0",
            sql,
        )

    def test_parse_and_render_post_with_where_against_passed_through_node(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WITH p WHERE p.id > 1 RETURN p.name")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0) with0 WHERE (with0.id > 1)",
            sql,
        )

    def test_parse_and_render_with_distinct(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH DISTINCT p RETURN p.name")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT with0.name FROM (SELECT DISTINCT t0.* FROM \"people\" t0 "
            "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
            "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id) with0",
            sql,
        )

    def test_parse_and_render_with_own_order_by_and_limit_independent_of_return(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WITH p ORDER BY p.name LIMIT 1 RETURN p.name")
        sql = Mapping(schema).to_sql(query).render(StandardGrammar())

        self.assertEqual(
            "SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0 ORDER BY t0.name ASC LIMIT 1) with0",
            sql,
        )

    def test_parse_raises_for_match_after_with(self) -> None:
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
        query = Query.parse("MATCH (p:Person) WITH p MATCH (p)-[:ACTED_IN]->(m:Movie) RETURN m")

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual("MATCH after WITH is not supported yet.", str(context.exception))

    def test_parse_raises_for_mixed_aggregation_in_with(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH p, count(m) AS total RETURN p, total")

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual(
            "Aggregation grouping in WITH is not supported yet; all WITH items must be aggregate expressions, or none.",
            str(context.exception),
        )

    def test_parse_raises_for_unaliased_computed_with_item(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges: []
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        query = Query.parse("MATCH (p:Person) WITH p.name RETURN p.name")

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual("WITH items must be aliased unless they pass through a plain variable.", str(context.exception))

    def test_parse_raises_for_relationship_variable_passthrough_in_with(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) WITH r RETURN r")

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual(
            "Relationship variables cannot be passed through WITH yet: r", str(context.exception)
        )

    def test_parse_raises_for_more_than_one_node_passthrough_in_with(self) -> None:
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
        query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH p, m RETURN p")

        with self.assertRaises(NotImplementedError) as context:
            Mapping(schema).to_sql(query)

        self.assertEqual(
            "WITH can pass through at most one node variable unchanged; alias the rest to a scalar expression.",
            str(context.exception),
        )


if __name__ == "__main__":
    unittest.main()
