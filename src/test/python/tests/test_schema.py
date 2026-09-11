import tempfile
import unittest
from pathlib import Path

from cypher2sql.schema import EdgeMapping, NodeMapping, PropertyMapping, RelationshipKind, SchemaDefinition


class NodeMappingTest(unittest.TestCase):
    def test_applies_defaults_when_optional_fields_are_none(self) -> None:
        mapping = NodeMapping(label="Person", table="people")

        self.assertEqual(["Person"], mapping.labels)
        self.assertEqual([], mapping.inherits)
        self.assertEqual(["id"], mapping.primary_keys)
        self.assertEqual({}, mapping.properties)
        self.assertEqual([], mapping.unique_keys)

    def test_qualified_table_includes_catalog_and_schema_when_present(self) -> None:
        mapping = NodeMapping(label="Person", table="people", catalog="warehouse", schema="graph")

        self.assertEqual("warehouse.graph.people", mapping.qualified_table)

    def test_qualified_primary_key_raises_for_composite_keys(self) -> None:
        mapping = NodeMapping(label="Person", table="people", primary_keys=["tenant_id", "id"])

        with self.assertRaisesRegex(ValueError, "Composite primary key is not scalar for label: Person"):
            mapping.qualified_primary_key("t0")

    def test_column_for_property_falls_back_to_property_name(self) -> None:
        mapping = NodeMapping(label="Person", table="people")

        self.assertEqual("name", mapping.column_for_property("name"))

    def test_column_for_property_uses_mapped_column_when_present(self) -> None:
        mapping = NodeMapping(
            label="Person", table="people",
            properties={"fullName": PropertyMapping("fullName", "full_name")},
        )

        self.assertEqual("full_name", mapping.column_for_property("fullName"))

    def test_join_on_columns_raises_on_arity_mismatch(self) -> None:
        mapping = NodeMapping(label="Person", table="people")

        with self.assertRaisesRegex(ValueError, r"Join key arity mismatch: 2 != 1"):
            mapping.join_on_columns("t0", ["a", "b"], "t1", ["c"])

    def test_join_on_columns_builds_qualified_equality_clauses(self) -> None:
        mapping = NodeMapping(label="Person", table="people")

        self.assertEqual(
            "t0.a = t1.c AND t0.b = t1.d",
            mapping.join_on_columns("t0", ["a", "b"], "t1", ["c", "d"]),
        )

    def test_matches_label_checks_labels_and_inherits(self) -> None:
        mapping = NodeMapping(
            label="Employee", labels=["Employee", "Staff"], inherits=["Person"], table="employees",
        )

        self.assertTrue(mapping.matches_label("Staff"))
        self.assertTrue(mapping.matches_label("Person"))
        self.assertFalse(mapping.matches_label("Movie"))


class EdgeMappingTest(unittest.TestCase):
    def test_self_referential_factory_defaults_cardinality(self) -> None:
        mapping = EdgeMapping.for_self_referential("MANAGES", "Person", "manager_id", "id")

        self.assertEqual("Person", mapping.from_label)
        self.assertEqual("Person", mapping.to_label)
        self.assertEqual("manager_id", mapping.from_key)
        self.assertEqual("id", mapping.to_key)

    def test_one_to_many_factory_defaults_cardinality(self) -> None:
        mapping = EdgeMapping.for_one_to_many("AUTHORED", "Person", "Movie", "id", "author_id")

        self.assertEqual("ONE_TO_MANY", mapping.cardinality.value)
        self.assertEqual("id", mapping.parent_primary_key)
        self.assertEqual("author_id", mapping.child_foreign_key)

    def test_many_to_one_factory_builds_many_to_one_cardinality(self) -> None:
        mapping = EdgeMapping.for_many_to_one(
            "AUTHORED", "Movie", "Person", ["author_id"], ["id"], {},
        )

        self.assertEqual(RelationshipKind.MANY_TO_ONE, mapping.relationship_kind)
        self.assertEqual("MANY_TO_ONE", mapping.cardinality.value)
        self.assertEqual(["author_id"], mapping.child_foreign_keys)
        self.assertEqual(["id"], mapping.parent_primary_keys)

    def test_scalar_join_key_is_none_for_composite_join_keys(self) -> None:
        mapping = EdgeMapping(
            type="ACTED_IN", from_label="Person", to_label="Movie",
            relationship_kind=RelationshipKind.JOIN_TABLE,
            from_join_keys=["tenant_id", "person_id"],
            to_join_keys=["movie_id"],
        )

        self.assertIsNone(mapping.from_join_key)
        self.assertEqual("movie_id", mapping.to_join_key)


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

    def test_loads_from_json_string(self) -> None:
        raw = """
        {
          "nodes": [
            {"label": "Person", "table": "people", "primaryKey": "id"},
            {"label": "Movie", "table": "movies", "primaryKey": "id"}
          ],
          "edges": [
            {
              "type": "ACTED_IN",
              "kind": "JOIN_TABLE",
              "fromLabel": "Person",
              "toLabel": "Movie",
              "joinTable": "people_movies",
              "fromJoinKey": "person_id",
              "toJoinKey": "movie_id"
            }
          ]
        }
        """
        schema = SchemaDefinition.from_json_string(raw)

        self.assertEqual("people", schema.node_for_label("Person").table)
        self.assertEqual("people_movies", schema.edge_for_type("ACTED_IN").join_table)

    def test_loads_from_yaml_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "schema.yaml"
            path.write_text(
                "nodes:\n"
                "  - label: Person\n"
                "    table: people\n"
                "    primaryKey: id\n"
                "edges: []\n",
                encoding="utf-8",
            )

            schema = SchemaDefinition.from_yaml_path(path)

            self.assertEqual("people", schema.node_for_label("Person").table)

    def test_loads_from_json_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "schema.json"
            path.write_text(
                '{"nodes": [{"label": "Person", "table": "people", "primaryKey": "id"}], "edges": []}',
                encoding="utf-8",
            )

            schema = SchemaDefinition.from_json_path(path)

            self.assertEqual("people", schema.node_for_label("Person").table)

    def test_loads_many_to_one_edge_from_yaml(self) -> None:
        raw = """
        nodes:
          - label: Movie
            table: movies
            primaryKey: id
          - label: Person
            table: people
            primaryKey: id
        edges:
          - type: AUTHORED
            kind: MANY_TO_ONE
            fromLabel: Movie
            toLabel: Person
            fromForeignKey: author_id
            toPrimaryKey: id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        edge = schema.edge_for_type("AUTHORED")

        self.assertEqual("MANY_TO_ONE", edge.relationship_kind.value)
        self.assertEqual("author_id", edge.child_foreign_key)
        self.assertEqual("id", edge.parent_primary_key)

    def test_loads_self_referential_edge_from_yaml(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKey: id
        edges:
          - type: MANAGES
            label: Person
            kind: SELF_REFERENTIAL
            fromKey: manager_id
            toKey: id
        """
        schema = SchemaDefinition.from_yaml_string(raw)
        edge = schema.edge_for_type("MANAGES")

        self.assertEqual("SELF_REFERENTIAL", edge.relationship_kind.value)
        self.assertEqual("manager_id", edge.from_key)

    def test_edge_for_type_raises_when_ambiguous(self) -> None:
        schema = (
            SchemaDefinition()
            .add_edge(EdgeMapping.for_one_to_many("AUTHORED", "Person", "Movie", "id", "author_id"))
            .add_edge(EdgeMapping.for_one_to_many("AUTHORED", "Person", "Book", "id", "author_id"))
        )

        with self.assertRaisesRegex(ValueError, "Ambiguous edge mapping for type: AUTHORED"):
            schema.edge_for_type("AUTHORED")

    def test_edge_for_type_with_labels_raises_when_missing(self) -> None:
        schema = SchemaDefinition()

        with self.assertRaisesRegex(
            ValueError, r"No edge mapping for type/labels: AUTHORED \(Person->Movie\)"
        ):
            schema.edge_for_type_with_labels("AUTHORED", "Person", "Movie")

    def test_edge_for_type_undirected_finds_forward_and_reverse(self) -> None:
        schema = SchemaDefinition().add_edge(
            EdgeMapping.for_one_to_many("AUTHORED", "Person", "Movie", "id", "author_id")
        )

        self.assertIsNotNone(schema.edge_for_type_undirected("AUTHORED", "Person", "Movie"))
        self.assertIsNotNone(schema.edge_for_type_undirected("AUTHORED", "Movie", "Person"))

    def test_edge_for_type_undirected_raises_when_ambiguous(self) -> None:
        schema = (
            SchemaDefinition()
            .add_edge(EdgeMapping.for_one_to_many("AUTHORED", "Person", "Movie", "id", "author_id"))
            .add_edge(EdgeMapping.for_one_to_many("AUTHORED", "Movie", "Person", "id", "author_id"))
        )

        with self.assertRaisesRegex(
            ValueError, r"Ambiguous undirected edge mapping for type/labels: AUTHORED \(Person<->Movie\)"
        ):
            schema.edge_for_type_undirected("AUTHORED", "Person", "Movie")

    def test_edge_for_type_undirected_raises_when_missing(self) -> None:
        schema = SchemaDefinition()

        with self.assertRaisesRegex(
            ValueError, r"No edge mapping for undirected type/labels: AUTHORED \(Person<->Movie\)"
        ):
            schema.edge_for_type_undirected("AUTHORED", "Person", "Movie")

    def test_schema_payload_must_be_a_mapping(self) -> None:
        with self.assertRaisesRegex(ValueError, "Schema payload must be a mapping."):
            SchemaDefinition.from_json_string("[1, 2, 3]")

    def test_each_node_entry_must_be_a_mapping(self) -> None:
        with self.assertRaisesRegex(ValueError, r"Schema node entry at index 0 must be a mapping."):
            SchemaDefinition.from_json_string('{"nodes": ["not a mapping"], "edges": []}')

    def test_each_edge_entry_must_be_a_mapping(self) -> None:
        with self.assertRaisesRegex(ValueError, r"Schema edge entry at index 0 must be a mapping."):
            SchemaDefinition.from_json_string('{"nodes": [], "edges": ["not a mapping"]}')

    def test_schema_list_values_must_be_non_blank_strings(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            primaryKeys: [id, ""]
        edges: []
        """
        with self.assertRaisesRegex(ValueError, "Schema value must be a string or list of strings."):
            SchemaDefinition.from_yaml_string(raw)

    def test_schema_property_mapping_must_be_string_or_mapping(self) -> None:
        raw = """
        nodes:
          - label: Person
            table: people
            properties:
              name: [not, valid]
        edges: []
        """
        with self.assertRaisesRegex(ValueError, "Schema property mapping must be a string or mapping."):
            SchemaDefinition.from_yaml_string(raw)


if __name__ == "__main__":
    unittest.main()
