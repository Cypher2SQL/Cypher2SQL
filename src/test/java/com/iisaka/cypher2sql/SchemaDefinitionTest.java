package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaDefinitionTest {
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
}
