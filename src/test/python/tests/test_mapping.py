import unittest

from cypher2sql.cypher_query import CypherEdge, CypherNode, CypherPattern, CypherQuery, CypherReturnItem, Direction
from cypher2sql.mapping import CypherSqlMapping
from cypher2sql.schema import EdgeMapping, NodeMapping, SchemaDefinition
from cypher2sql.sql_query import BasicSqlDialect


class CypherSqlMappingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.schema = (
            SchemaDefinition()
            .add_node(NodeMapping("Person", "people", "id"))
            .add_node(NodeMapping("Movie", "movies", "id"))
            .add_edge(
                EdgeMapping.for_join_table(
                    "ACTED_IN",
                    "Person",
                    "Movie",
                    "people_movies",
                    "person_id",
                    "movie_id",
                )
            )
            .add_edge(EdgeMapping.for_self_referential("MANAGES", "Person", "manager_id", "id"))
            .add_edge(EdgeMapping.for_one_to_many("AUTHORED", "Person", "Movie", "id", "author_id"))
        )

    def _query(self, left_var: str, left_label: str, rel_type: str, right_var: str, right_label: str) -> CypherQuery:
        pattern = CypherPattern(
            nodes=[CypherNode(left_var, left_label), CypherNode(right_var, right_label)],
            edges=[CypherEdge(variable=None, type=rel_type, direction=Direction.LEFT_TO_RIGHT)],
        )
        return CypherQuery("MATCH ...", [pattern], parse_tree=object())

    def test_renders_join_table(self) -> None:
        query = self._query("p", "Person", "ACTED_IN", "m", "Movie")
        sql = CypherSqlMapping(self.schema).to_sql(query).render(BasicSqlDialect())
        self.assertEqual(
            'SELECT t0.* FROM "people" t0 INNER JOIN "people_movies" j2 ON t0.id = j2.person_id '
            'INNER JOIN "movies" t1 ON j2.movie_id = t1.id',
            sql,
        )

    def test_renders_self_referential(self) -> None:
        query = self._query("p", "Person", "MANAGES", "m", "Person")
        sql = CypherSqlMapping(self.schema).to_sql(query).render(BasicSqlDialect())
        self.assertEqual('SELECT t0.* FROM "people" t0 INNER JOIN "people" t1 ON t0.manager_id = t1.id', sql)

    def test_renders_one_to_many_parent_on_left(self) -> None:
        query = self._query("p", "Person", "AUTHORED", "m", "Movie")
        sql = CypherSqlMapping(self.schema).to_sql(query).render(BasicSqlDialect())
        self.assertEqual('SELECT t0.* FROM "people" t0 INNER JOIN "movies" t1 ON t1.author_id = t0.id', sql)

    def test_renders_one_to_many_parent_on_right(self) -> None:
        query = self._query("m", "Movie", "AUTHORED", "p", "Person")
        sql = CypherSqlMapping(self.schema).to_sql(query).render(BasicSqlDialect())
        self.assertEqual('SELECT t0.* FROM "movies" t0 INNER JOIN "people" t1 ON t0.author_id = t1.id', sql)

    def test_raises_when_edge_labels_do_not_match(self) -> None:
        query = self._query("p", "Person", "AUTHORED", "x", "Person")
        with self.assertRaisesRegex(ValueError, "Edge mapping labels do not match nodes: AUTHORED"):
            CypherSqlMapping(self.schema).to_sql(query)

    def test_raises_when_no_patterns(self) -> None:
        query = CypherQuery("RETURN 1", [], parse_tree=object())
        with self.assertRaisesRegex(ValueError, "No patterns parsed from Cypher query."):
            CypherSqlMapping(self.schema).to_sql(query)

    def test_raises_for_variable_length_traversal_placeholder(self) -> None:
        query = CypherQuery("MATCH (c:Person)-[*0..3]->(t:Movie) RETURN c, t", [], parse_tree=object())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.",
        ):
            CypherSqlMapping(self.schema).to_sql(query)

    def test_raises_for_multi_hop_traversal_placeholder(self) -> None:
        pattern = CypherPattern(
            nodes=[CypherNode("p", "Person"), CypherNode("m", "Movie"), CypherNode("o", "Person")],
            edges=[
                CypherEdge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT),
                CypherEdge(variable=None, type="ACTED_IN", direction=Direction.RIGHT_TO_LEFT),
            ],
        )
        query = CypherQuery("MATCH (p)-[:ACTED_IN]->(m)<-[:ACTED_IN]-(o) RETURN p, m, o", [pattern], parse_tree=object())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Multi-hop traversals are not supported yet; traversal planning is a future enhancement.",
        ):
            CypherSqlMapping(self.schema).to_sql(query)

    def test_projects_return_properties(self) -> None:
        query = CypherQuery(
            "MATCH ... RETURN p.id, m.id",
            [
                CypherPattern(
                    nodes=[CypherNode("p", "Person"), CypherNode("m", "Movie")],
                    edges=[CypherEdge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT)],
                )
            ],
            parse_tree=object(),
            return_items=[CypherReturnItem("p", "id"), CypherReturnItem("m", "id")],
        )
        sql = CypherSqlMapping(self.schema).to_sql(query).render(BasicSqlDialect())
        self.assertEqual(
            'SELECT t0.id, t1.id FROM "people" t0 INNER JOIN "people_movies" j2 ON t0.id = j2.person_id '
            'INNER JOIN "movies" t1 ON j2.movie_id = t1.id',
            sql,
        )

    def test_projects_return_variables(self) -> None:
        query = CypherQuery(
            "MATCH ... RETURN p, m",
            [
                CypherPattern(
                    nodes=[CypherNode("p", "Person"), CypherNode("m", "Movie")],
                    edges=[CypherEdge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT)],
                )
            ],
            parse_tree=object(),
            return_items=[CypherReturnItem("p"), CypherReturnItem("m")],
        )
        sql = CypherSqlMapping(self.schema).to_sql(query).render(BasicSqlDialect())
        self.assertEqual(
            'SELECT t0.*, t1.* FROM "people" t0 INNER JOIN "people_movies" j2 ON t0.id = j2.person_id '
            'INNER JOIN "movies" t1 ON j2.movie_id = t1.id',
            sql,
        )

    def test_raises_for_unknown_return_variable(self) -> None:
        query = CypherQuery(
            "MATCH ... RETURN x.id",
            [
                CypherPattern(
                    nodes=[CypherNode("p", "Person"), CypherNode("m", "Movie")],
                    edges=[CypherEdge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT)],
                )
            ],
            parse_tree=object(),
            return_items=[CypherReturnItem("x", "id")],
        )
        with self.assertRaisesRegex(ValueError, "RETURN references unknown variable: x"):
            CypherSqlMapping(self.schema).to_sql(query)


if __name__ == "__main__":
    unittest.main()
