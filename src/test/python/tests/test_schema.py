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

    def test_loads_rich_schema_metadata(self) -> None:
        raw = """
        nodes:
          - label: Person
            labels: [Person, Employee]
            inherits: [Entity]
            catalog: analytics
            schema: graph
            table: people
            primaryKeys: [tenant_id, id]
            properties:
              name:
                column: full_name
                type: string
                nullable: false
                quote: true
            uniqueKeys:
              - [tenant_id, employee_number]
        edges:
          - type: ACTED_IN
            kind: JOIN_TABLE
            fromLabel: Person
            toLabel: Movie
            joinTable: people_movies
            fromJoinKeys: [person_tenant_id, person_id]
            toJoinKeys: [movie_tenant_id, movie_id]
            cardinality: MANY_TO_MANY
            unique: true
            properties:
              role:
                column: character_name
                type: string
                nullable: true
        """
        schema = SchemaDefinition.from_yaml_string(raw)

        person = schema.node_for_label("Person")
        self.assertEqual("analytics.graph.people", person.qualified_table)
        self.assertEqual(["tenant_id", "id"], person.primary_keys)
        self.assertEqual("full_name", person.column_for_property("name"))
        self.assertEqual("string", person.properties["name"].type)
        self.assertEqual(["Entity"], person.inherits)
        self.assertEqual([["tenant_id", "employee_number"]], person.unique_keys)

        edge = schema.edge_for_type("ACTED_IN")
        self.assertEqual(["person_tenant_id", "person_id"], edge.from_join_keys)
        self.assertEqual("character_name", edge.properties["role"].column)
        self.assertEqual("MANY_TO_MANY", edge.cardinality.value)
        self.assertTrue(edge.unique)


if __name__ == "__main__":
    unittest.main()
