package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaDefinitionTest {
    @TempDir
    Path tempDir;
    @Test
    void loadsSchemaFromYamlResource() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");

        final NodeMapping person = schema.nodeForLabel("Person");
        assertEquals("people", person.table());
        assertEquals("id", person.primaryKey());

        final EdgeMapping edge = schema.edgeForType("ACTED_IN");
        assertEquals(EdgeMapping.RelationshipKind.JOIN_TABLE, edge.relationshipKind());
        assertEquals("people_movies", edge.joinTable());
        assertNotNull(edge.fromJoinKey());
        assertNotNull(edge.toJoinKey());
    }

    @Test
    void loadsSchemaFromJsonString() {
        final String raw = """
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
                    },
                    {
                      "type": "MANAGES",
                      "kind": "SELF_REFERENTIAL",
                      "fromLabel": "Person",
                      "fromKey": "manager_id",
                      "toKey": "id"
                    },
                    {
                      "type": "AUTHORED",
                      "kind": "ONE_TO_MANY",
                      "parentLabel": "Person",
                      "childLabel": "Movie",
                      "parentPrimaryKey": "id",
                      "childForeignKey": "author_id"
                    }
                  ]
                }
                """;

        final SchemaDefinition schema = SchemaDefinition.fromJsonString(raw);

        final NodeMapping person = schema.nodeForLabel("Person");
        assertEquals("people", person.table());
        assertEquals("id", person.primaryKey());

        final EdgeMapping actedIn = schema.edgeForType("ACTED_IN");
        assertEquals(EdgeMapping.RelationshipKind.JOIN_TABLE, actedIn.relationshipKind());
        assertEquals("people_movies", actedIn.joinTable());

        final EdgeMapping manages = schema.edgeForType("MANAGES");
        assertEquals(EdgeMapping.RelationshipKind.SELF_REFERENTIAL, manages.relationshipKind());
        assertEquals("manager_id", manages.fromKey());
        assertEquals("id", manages.toKey());

        final EdgeMapping authored = schema.edgeForType("AUTHORED");
        assertEquals(EdgeMapping.RelationshipKind.ONE_TO_MANY, authored.relationshipKind());
        assertEquals("id", authored.parentPrimaryKey());
        assertEquals("author_id", authored.childForeignKey());
    }

    @Test
    void throwsWhenEdgeKindIsMissing() {
        final String raw = """
                {
                  "nodes": [],
                  "edges": [
                    {"type": "ACTED_IN"}
                  ]
                }
                """;

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SchemaDefinition.fromJsonString(raw));
        assertEquals("Edge mapping missing kind for type: ACTED_IN", ex.getMessage());
    }

    @Test
    void loadsRichSchemaMetadata() {
        final String raw = """
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
                """;

        final SchemaDefinition schema = SchemaDefinition.fromYamlString(raw);

        final NodeMapping person = schema.nodeForLabel("Person");
        assertEquals("analytics.graph.people", person.table());
        assertEquals(java.util.List.of("tenant_id", "id"), person.primaryKeys());
        assertEquals("full_name", person.columnForProperty("name"));
        assertEquals("string", person.properties().get("name").type());
        assertEquals(java.util.List.of("Entity"), person.inherits());
        assertEquals(java.util.List.of(java.util.List.of("tenant_id", "employee_number")), person.uniqueKeys());

        final EdgeMapping edge = schema.edgeForType("ACTED_IN");
        assertEquals(java.util.List.of("person_tenant_id", "person_id"), edge.fromJoinKeys());
        assertEquals("character_name", edge.columnForProperty("role"));
        assertEquals(EdgeMapping.Cardinality.MANY_TO_MANY, edge.cardinality());
        assertEquals(true, edge.unique());
    }

    @Test
    void loadsSchemaFromJsonResource() {
        final SchemaDefinition schema = SchemaDefinition.fromJsonResource("schema.json");

        assertEquals("people", schema.nodeForLabel("Person").table());
        assertEquals(EdgeMapping.RelationshipKind.JOIN_TABLE, schema.edgeForType("ACTED_IN").relationshipKind());
    }

    @Test
    void jsonResourceNotFoundThrows() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromJsonResource("does-not-exist.json"));
        assertEquals("Resource not found: does-not-exist.json", ex.getMessage());
    }

    @Test
    void yamlResourceNotFoundThrows() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromYamlResource("does-not-exist.yaml"));
        assertEquals("Resource not found: does-not-exist.yaml", ex.getMessage());
    }

    @Test
    void loadsSchemaFromYamlPath() throws IOException {
        final Path path = tempDir.resolve("schema.yaml");
        Files.writeString(path, """
                nodes:
                  - label: Person
                    table: people
                    primaryKey: id
                edges: []
                """);

        final SchemaDefinition schema = SchemaDefinition.fromYamlPath(path);

        assertEquals("people", schema.nodeForLabel("Person").table());
    }

    @Test
    void loadsSchemaFromJsonPath() throws IOException {
        final Path path = tempDir.resolve("schema.json");
        Files.writeString(path, """
                {"nodes": [{"label": "Person", "table": "people", "primaryKey": "id"}], "edges": []}
                """);

        final SchemaDefinition schema = SchemaDefinition.fromJsonPath(path);

        assertEquals("people", schema.nodeForLabel("Person").table());
    }

    @Test
    void loadsManyToOneEdgeFromYaml() {
        final String raw = """
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
                """;

        final SchemaDefinition schema = SchemaDefinition.fromYamlString(raw);
        final EdgeMapping edge = schema.edgeForType("AUTHORED");

        assertEquals(EdgeMapping.RelationshipKind.MANY_TO_ONE, edge.relationshipKind());
        assertEquals("author_id", edge.childForeignKey());
        assertEquals("id", edge.parentPrimaryKey());
    }

    @Test
    void loadsSelfReferentialEdgeFromYaml() {
        final String raw = """
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
                """;

        final SchemaDefinition schema = SchemaDefinition.fromYamlString(raw);
        final EdgeMapping edge = schema.edgeForType("MANAGES");

        assertEquals(EdgeMapping.RelationshipKind.SELF_REFERENTIAL, edge.relationshipKind());
        assertEquals("manager_id", edge.fromKey());
    }

    @Test
    void nodeForLabelThrowsWhenMissing() {
        final SchemaDefinition schema = new SchemaDefinition();

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> schema.nodeForLabel("Ghost"));
        assertEquals("No node mapping for label: Ghost", ex.getMessage());
    }

    @Test
    void edgeForTypeThrowsWhenMissing() {
        final SchemaDefinition schema = new SchemaDefinition();

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> schema.edgeForType("GHOST"));
        assertEquals("No edge mapping for type: GHOST", ex.getMessage());
    }

    @Test
    void edgeForTypeThrowsWhenAmbiguous() {
        final SchemaDefinition schema = new SchemaDefinition()
                .addEdge(EdgeMapping.forOneToMany("AUTHORED", "Person", "Movie", "id", "author_id"))
                .addEdge(EdgeMapping.forOneToMany("AUTHORED", "Person", "Book", "id", "author_id"));

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> schema.edgeForType("AUTHORED"));
        assertTrue(ex.getMessage().startsWith("Ambiguous edge mapping for type: AUTHORED"));
    }

    @Test
    void edgeForTypeAndLabelsThrowsWhenMissing() {
        final SchemaDefinition schema = new SchemaDefinition();

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> schema.edgeForType("AUTHORED", "Person", "Movie"));
        assertEquals("No edge mapping for type/labels: AUTHORED (Person->Movie)", ex.getMessage());
    }

    @Test
    void edgeForTypeUndirectedFindsForwardAndReverseMatches() {
        final SchemaDefinition schema = new SchemaDefinition()
                .addEdge(EdgeMapping.forOneToMany("AUTHORED", "Person", "Movie", "id", "author_id"));

        assertNotNull(schema.edgeForTypeUndirected("AUTHORED", "Person", "Movie"));
        assertNotNull(schema.edgeForTypeUndirected("AUTHORED", "Movie", "Person"));
    }

    @Test
    void edgeForTypeUndirectedThrowsWhenAmbiguous() {
        final SchemaDefinition schema = new SchemaDefinition()
                .addEdge(EdgeMapping.forOneToMany("AUTHORED", "Person", "Movie", "id", "author_id"))
                .addEdge(EdgeMapping.forOneToMany("AUTHORED", "Movie", "Person", "id", "author_id"));

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> schema.edgeForTypeUndirected("AUTHORED", "Person", "Movie"));
        assertEquals(
                "Ambiguous undirected edge mapping for type/labels: AUTHORED (Person<->Movie)", ex.getMessage());
    }

    @Test
    void edgeForTypeUndirectedThrowsWhenMissing() {
        final SchemaDefinition schema = new SchemaDefinition();

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> schema.edgeForTypeUndirected("AUTHORED", "Person", "Movie"));
        assertEquals(
                "No edge mapping for undirected type/labels: AUTHORED (Person<->Movie)", ex.getMessage());
    }

    @Test
    void schemaYamlMustBeAMapping() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromYamlString("- not a mapping"));
        assertEquals("Schema YAML must be a mapping.", ex.getMessage());
    }

    @Test
    void schemaJsonMustBeAMapping() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromJsonString("[1, 2, 3]"));
        assertEquals("Schema JSON must be a mapping.", ex.getMessage());
    }

    @Test
    void eachNodeEntryMustBeAMapping() {
        final String raw = """
                nodes:
                  - "not a mapping"
                edges: []
                """;

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromYamlString(raw));
        assertEquals("Each node entry must be a mapping.", ex.getMessage());
    }

    @Test
    void eachEdgeEntryMustBeAMapping() {
        final String raw = """
                nodes: []
                edges:
                  - "not a mapping"
                """;

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromYamlString(raw));
        assertEquals("Each edge entry must be a mapping.", ex.getMessage());
    }

    @Test
    void schemaListValuesMustBeNonBlankStrings() {
        final String raw = """
                nodes:
                  - label: Person
                    table: people
                    primaryKeys: [id, ""]
                edges: []
                """;

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromYamlString(raw));
        assertEquals("Schema list values must be non-blank strings.", ex.getMessage());
    }

    @Test
    void schemaPropertyMappingMustBeStringOrMapping() {
        final String raw = """
                nodes:
                  - label: Person
                    table: people
                    properties:
                      name: [not, valid]
                edges: []
                """;

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaDefinition.fromYamlString(raw));
        assertEquals("Schema property mapping must be a string or mapping.", ex.getMessage());
    }
}
