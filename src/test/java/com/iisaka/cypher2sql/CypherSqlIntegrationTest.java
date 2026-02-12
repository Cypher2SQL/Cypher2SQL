package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.CypherQuery;
import com.iisaka.cypher2sql.query.sql.BasicSqlDialect;
import com.iisaka.cypher2sql.schema.CypherSqlMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import com.iisaka.cypher2sql.schema.SchemaDefinitionYaml;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CypherSqlIntegrationTest {
    @Test
    void parsesCypherAndRendersSql() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) RETURN p");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final String sql = mapping.toSql(query).render(new BasicSqlDialect());

        assertNotNull(query.parseTree());
        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersSelfReferentialJoin() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("MATCH (p:Person)-[:MANAGES]->(m:Person) RETURN p");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final String sql = mapping.toSql(query).render(new BasicSqlDialect());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsLeftNode() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("MATCH (p:Person)-[:AUTHORED]->(m:Movie) RETURN p");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final String sql = mapping.toSql(query).render(new BasicSqlDialect());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsRightNode() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("MATCH (m:Movie)-[:AUTHORED]->(p:Person) RETURN m");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final String sql = mapping.toSql(query).render(new BasicSqlDialect());

        assertEquals(
                "SELECT t0.* FROM \"movies\" t0 INNER JOIN \"people\" t1 ON t0.author_id = t1.id",
                sql
        );
    }

    @Test
    void throwsWhenPatternIsMissing() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("RETURN p");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> mapping.toSql(query));
        assertEquals("No patterns parsed from Cypher query.", ex.getMessage());
    }

    @Test
    void throwsForVariableLengthTraversalPlaceholder() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("MATCH (c:Person)-[*0..3]->(t:Movie) RETURN c, t");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> mapping.toSql(query));
        assertEquals(
                "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.",
                ex.getMessage());
    }

    @Test
    void throwsForMultiHopTraversalPlaceholder() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query =
                CypherQuery.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie)<-[:ACTED_IN]-(o:Person) RETURN p, m, o");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> mapping.toSql(query));
        assertEquals(
                "Multi-hop traversals are not supported yet; traversal planning is a future enhancement.",
                ex.getMessage());
    }

    @Test
    void rendersReturnPropertiesOnly() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id, m.id");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final String sql = mapping.toSql(query).render(new BasicSqlDialect());

        assertEquals(
                "SELECT t0.id, t1.id FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersReturnVariablesOnly() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final CypherQuery query = CypherQuery.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p, m");
        final CypherSqlMapping mapping = new CypherSqlMapping(schema);

        final String sql = mapping.toSql(query).render(new BasicSqlDialect());

        assertEquals(
                "SELECT t0.*, t1.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void throwsForUnsupportedReturnExpression() {
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CypherQuery.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(p)")
        );
        assertEquals(
                "Unsupported RETURN expression: count(p). Only variable or variable.property are supported.",
                ex.getMessage());
    }
}
