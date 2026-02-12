import unittest

from cypher2sql.cypher_query import Edge, Node, Pattern, Query, ReturnItem, Direction
from cypher2sql.mapping import Mapping
from cypher2sql.schema import EdgeMapping, NodeMapping, SchemaDefinition
from cypher2sql.sql_query import BasicDialect


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

    def _query(self, left_var: str, left_label: str, rel_type: str, right_var: str, right_label: str) -> Query:
        pattern = Pattern(
            nodes=[Node(left_var, left_label), Node(right_var, right_label)],
            edges=[Edge(variable=None, type=rel_type, direction=Direction.LEFT_TO_RIGHT)],
        )
        return Query("MATCH ...", [pattern], parse_tree=object())

    def test_renders_join_table(self) -> None:
        query = self._query("p", "Person", "ACTED_IN", "m", "Movie")
        sql = Mapping(self.schema).to_sql(query).render(BasicDialect())
        self.assertEqual(
            'SELECT t0.* FROM "people" t0 INNER JOIN "people_movies" j2 ON t0.id = j2.person_id '
            'INNER JOIN "movies" t1 ON j2.movie_id = t1.id',
            sql,
        )

    def test_renders_self_referential(self) -> None:
        query = self._query("p", "Person", "MANAGES", "m", "Person")
        sql = Mapping(self.schema).to_sql(query).render(BasicDialect())
        self.assertEqual('SELECT t0.* FROM "people" t0 INNER JOIN "people" t1 ON t0.manager_id = t1.id', sql)

    def test_renders_one_to_many_parent_on_left(self) -> None:
        query = self._query("p", "Person", "AUTHORED", "m", "Movie")
        sql = Mapping(self.schema).to_sql(query).render(BasicDialect())
        self.assertEqual('SELECT t0.* FROM "people" t0 INNER JOIN "movies" t1 ON t1.author_id = t0.id', sql)

    def test_renders_one_to_many_parent_on_right(self) -> None:
        query = self._query("m", "Movie", "AUTHORED", "p", "Person")
        sql = Mapping(self.schema).to_sql(query).render(BasicDialect())
        self.assertEqual('SELECT t0.* FROM "movies" t0 INNER JOIN "people" t1 ON t0.author_id = t1.id', sql)

    def test_raises_when_edge_labels_do_not_match(self) -> None:
        query = self._query("p", "Person", "AUTHORED", "x", "Person")
        with self.assertRaisesRegex(ValueError, "Edge mapping labels do not match nodes: AUTHORED"):
            Mapping(self.schema).to_sql(query)

    def test_raises_when_no_patterns(self) -> None:
        query = Query("RETURN 1", [], parse_tree=object())
        with self.assertRaisesRegex(ValueError, "No patterns parsed from Cypher query."):
            Mapping(self.schema).to_sql(query)

    def test_raises_for_variable_length_traversal_placeholder(self) -> None:
        query = Query("MATCH (c:Person)-[*0..3]->(t:Movie) RETURN c, t", [], parse_tree=object())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.",
        ):
            Mapping(self.schema).to_sql(query)

    def test_raises_for_multi_hop_traversal_placeholder(self) -> None:
        pattern = Pattern(
            nodes=[Node("p", "Person"), Node("m", "Movie"), Node("o", "Person")],
            edges=[
                Edge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT),
                Edge(variable=None, type="ACTED_IN", direction=Direction.RIGHT_TO_LEFT),
            ],
        )
        query = Query("MATCH (p)-[:ACTED_IN]->(m)<-[:ACTED_IN]-(o) RETURN p, m, o", [pattern], parse_tree=object())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Multi-hop traversals are not supported yet; traversal planning is a future enhancement.",
        ):
            Mapping(self.schema).to_sql(query)

    def test_projects_return_properties(self) -> None:
        query = Query(
            "MATCH ... RETURN p.id, m.id",
            [
                Pattern(
                    nodes=[Node("p", "Person"), Node("m", "Movie")],
                    edges=[Edge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT)],
                )
            ],
            parse_tree=object(),
            return_items=[ReturnItem("p", "id"), ReturnItem("m", "id")],
        )
        sql = Mapping(self.schema).to_sql(query).render(BasicDialect())
        self.assertEqual(
            'SELECT t0.id, t1.id FROM "people" t0 INNER JOIN "people_movies" j2 ON t0.id = j2.person_id '
            'INNER JOIN "movies" t1 ON j2.movie_id = t1.id',
            sql,
        )

    def test_projects_return_variables(self) -> None:
        query = Query(
            "MATCH ... RETURN p, m",
            [
                Pattern(
                    nodes=[Node("p", "Person"), Node("m", "Movie")],
                    edges=[Edge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT)],
                )
            ],
            parse_tree=object(),
            return_items=[ReturnItem("p"), ReturnItem("m")],
        )
        sql = Mapping(self.schema).to_sql(query).render(BasicDialect())
        self.assertEqual(
            'SELECT t0.*, t1.* FROM "people" t0 INNER JOIN "people_movies" j2 ON t0.id = j2.person_id '
            'INNER JOIN "movies" t1 ON j2.movie_id = t1.id',
            sql,
        )

    def test_raises_for_unknown_return_variable(self) -> None:
        query = Query(
            "MATCH ... RETURN x.id",
            [
                Pattern(
                    nodes=[Node("p", "Person"), Node("m", "Movie")],
                    edges=[Edge(variable=None, type="ACTED_IN", direction=Direction.LEFT_TO_RIGHT)],
                )
            ],
            parse_tree=object(),
            return_items=[ReturnItem("x", "id")],
        )
        with self.assertRaisesRegex(ValueError, "RETURN references unknown variable: x"):
            Mapping(self.schema).to_sql(query)


if __name__ == "__main__":
    unittest.main()
