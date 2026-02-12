package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import com.iisaka.cypher2sql.schema.SchemaDefinitionJson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaDefinitionJsonTest {
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

        final SchemaDefinition schema = SchemaDefinitionJson.fromString(raw);

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

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SchemaDefinitionJson.fromString(raw));
        assertEquals("Edge mapping missing kind for type: ACTED_IN", ex.getMessage());
    }
}
