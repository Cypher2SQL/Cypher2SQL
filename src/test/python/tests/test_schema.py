import unittest

from cypher2sql.schema import SchemaDefinition

class SchemaDefinitionTest(unittest.TestCase):
    def test_loads_from_yaml_string(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
          - label: Movie
            table: movies
            primaryKey: id
        edges:
          - type: PERSON_ACTED_IN_MOVIE
            kind: JOIN_TABLE
            fromLabel: Person
            toLabel: Movie
            joinTable: people_movies
            fromJoinKey: person_id
            toJoinKey: movie_id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        person = schema.node_for_label("Person")
        self.assertEqual("people", person.table)
        self.assertEqual("id", person.primary_key)

        edge = schema.edge_for_type("PERSON_ACTED_IN_MOVIE")
        self.assertEqual("people_movies", edge.join_table)
        self.assertEqual("person_id", edge.from_join_key)
        self.assertEqual("movie_id", edge.to_join_key)

    def test_missing_node_and_edge_lookups_raise(self) -> None:
        schema = SchemaDefinition()
        with self.assertRaisesRegex(ValueError, "No node mapping for label: Person"):
            schema.node_for_label("Person")
        with self.assertRaisesRegex(ValueError, "No edge mapping for type: ACTED_IN"):
            schema.edge_for_type("ACTED_IN")

    def test_invalid_yaml_payload_raises(self) -> None:
        with self.assertRaisesRegex(ValueError, "Schema YAML must be a mapping."):
            SchemaDefinition.from_yaml_string("- Person")


if __name__ == "__main__":
    unittest.main()
