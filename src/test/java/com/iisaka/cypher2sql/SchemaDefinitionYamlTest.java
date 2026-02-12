package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import com.iisaka.cypher2sql.schema.SchemaDefinitionYaml;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaDefinitionYamlTest {
    @Test
    void loadsSchemaFromResource() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");

        final NodeMapping person = schema.nodeForLabel("Person");
        assertEquals("people", person.table());
        assertEquals("id", person.primaryKey());

        final EdgeMapping edge = schema.edgeForType("ACTED_IN");
        assertEquals(EdgeMapping.RelationshipKind.JOIN_TABLE, edge.relationshipKind());
        assertEquals("people_movies", edge.joinTable());
        assertNotNull(edge.fromJoinKey());
        assertNotNull(edge.toJoinKey());
    }
}
