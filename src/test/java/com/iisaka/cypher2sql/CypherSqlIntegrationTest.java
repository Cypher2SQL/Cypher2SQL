package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.sql.BasicDialect;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.Mapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import com.iisaka.cypher2sql.schema.SchemaDefinitionYaml;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CypherSqlIntegrationTest {
    @Test
    void parsesCypherAndRendersSql() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) RETURN p");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

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
        final Query query = Query.of("MATCH (p:Person)-[:MANAGES]->(m:Person) RETURN p");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsLeftNode() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:AUTHORED]->(m:Movie) RETURN p");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsRightNode() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (m:Movie)-[:AUTHORED]->(p:Person) RETURN m");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals(
                "SELECT t0.* FROM \"movies\" t0 INNER JOIN \"people\" t1 ON t0.author_id = t1.id",
                sql
        );
    }

    @Test
    void throwsWhenPatternIsMissing() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("RETURN p");
        final Mapping mapping = new Mapping(schema);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> mapping.toSql(query));
        assertEquals("No patterns parsed from Cypher query.", ex.getMessage());
    }

    @Test
    void throwsForVariableLengthTraversalPlaceholder() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (c:Person)-[*0..3]->(t:Movie) RETURN c, t");
        final Mapping mapping = new Mapping(schema);

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> mapping.toSql(query));
        assertEquals(
                "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.",
                ex.getMessage());
    }

    @Test
    void rendersExplicitMultiHopTraversal() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query =
                Query.of("MATCH (p:Person)-[:AUTHORED]->(m:Movie)-[:AUTHORED]->(o:Person) RETURN p, m, o");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals(
                "SELECT t0.*, t1.*, t2.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id "
                        + "INNER JOIN \"people\" t2 ON t1.author_id = t2.id",
                sql);
    }

    @Test
    void rendersReturnPropertiesOnly() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id, m.id");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals(
                "SELECT t0.id, t1.id FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersReturnVariablesOnly() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p, m");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals(
                "SELECT t0.*, t1.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersWhenLeftNodeIsAnonymous() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (:Person)-[:ACTED_IN]->(m:Movie) RETURN m");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals(
                "SELECT t1.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersSingleNodeMatch() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());

        assertEquals("SELECT t0.* FROM \"people\" t0", sql);
    }

    @Test
    void rendersJoinTableRowsWhenReturningEdgeVariable() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH ()-[r:ACTED_IN]->() RETURN r");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());
        assertEquals(
                "SELECT j2.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersForeignKeyColumnsForSameEdgeVariableQuery() {
        final SchemaDefinition schema = new SchemaDefinition()
                .addNode(new NodeMapping("Person", "people", "id"))
                .addNode(new NodeMapping("Movie", "movies", "id"))
                .addEdge(EdgeMapping.forOneToMany("ACTED_IN", "Person", "Movie", "id", "author_id"));
        final Query query = Query.of("MATCH ()-[r:ACTED_IN]->() RETURN r");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());
        assertEquals(
                "SELECT t1.author_id, t0.id FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql);
    }

    @Test
    void rendersForeignKeyColumnsWhenReturningEdgeVariable() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[r:AUTHORED]->(m:Movie) RETURN r");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());
        assertEquals(
                "SELECT t1.author_id, t0.id FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql);
    }

    @Test
    void rendersSelfReferentialColumnsForSameEdgeVariableQuery() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH ()-[r:MANAGES]->() RETURN r");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());
        assertEquals(
                "SELECT t0.manager_id, t1.id FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
                sql);
    }

    @Test
    void throwsForUnsupportedReturnExpression() {
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(p)")
        );
        assertEquals(
                "Unsupported RETURN expression: count(p). Only variable or variable.property are supported.",
                ex.getMessage());
    }

    @Disabled("Enable when RETURN function projection support (e.g. count(*)) is implemented.")
    @Test
    void rendersCountStarProjection() {
        final SchemaDefinition schema = SchemaDefinitionYaml.fromResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(*)");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());
        assertEquals(
                "SELECT COUNT(*) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }
}
