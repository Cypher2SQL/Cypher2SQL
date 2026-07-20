package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.sql.BasicDialect;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CypherSqlIntegrationTest {
    @Test
    void parsesCypherAndRendersSql() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) RETURN p");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertNotNull(query.parseTree());
        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersSelfReferentialJoin() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:MANAGES]->(m:Person) RETURN p");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsLeftNode() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:AUTHORED]->(m:Movie) RETURN p");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsRightNode() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (m:Movie)-[:AUTHORED]->(p:Person) RETURN m");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t0.* FROM \"movies\" t0 INNER JOIN \"people\" t1 ON t0.author_id = t1.id",
                sql
        );
    }

    @Test
    void respectsEdgeDirectionWhenTypeHasBothOrientations() {
        final SchemaDefinition schema = new SchemaDefinition()
                .addNode(new NodeMapping("Person", "people", "id"))
                .addNode(new NodeMapping("Movie", "movies", "id"))
                .addEdge(EdgeMapping.forOneToMany("LINKED", "Person", "Movie", "id", "author_id"))
                .addEdge(EdgeMapping.forOneToMany("LINKED", "Movie", "Person", "id", "favorite_movie_id"));

        final Query leftToRight = Query.of("MATCH (p:Person)-[:LINKED]->(m:Movie) RETURN p, m");
        final Query rightToLeft = Query.of("MATCH (p:Person)<-[:LINKED]-(m:Movie) RETURN p, m");

        final String ltrSql = leftToRight.asSql(schema).render(new BasicDialect());
        final String rtlSql = rightToLeft.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t0.*, t1.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                ltrSql
        );
        assertEquals(
                "SELECT t0.*, t1.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t0.favorite_movie_id = t1.id",
                rtlSql
        );
    }

    @Test
    void throwsWhenPatternIsMissing() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("RETURN p");

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> query.asSql(schema));
        assertEquals("No patterns parsed from Cypher query.", ex.getMessage());
    }

    @Test
    void throwsForVariableLengthTraversalPlaceholder() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (c:Person)-[*0..3]->(t:Movie) RETURN c, t");

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals(
                "Variable-length traversals are not supported yet; recursive SQL translation is a future enhancement.",
                ex.getMessage());
    }

    @Test
    void throwsForWithClausePlaceholder() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p.id AS pid WHERE p.id > 1 RETURN pid");

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals(
                "WITH clauses are parsed but not rendered yet; pipeline semantics are a future enhancement.",
                ex.getMessage());
    }

    @Test
    void rendersExplicitMultiHopTraversal() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query =
                Query.of("MATCH (p:Person)-[:AUTHORED]->(m:Movie)-[:AUTHORED]->(o:Person) RETURN p, m, o");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t0.*, t1.*, t2.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id "
                        + "INNER JOIN \"people\" t2 ON t1.author_id = t2.id",
                sql);
    }

    @Test
    void rendersReturnPropertiesOnly() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id, m.id");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t0.id, t1.id FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersReturnVariablesOnly() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p, m");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t0.*, t1.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersWhenLeftNodeIsAnonymous() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (:Person)-[:ACTED_IN]->(m:Movie) RETURN m");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT t1.* FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersSingleNodeMatch() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals("SELECT t0.* FROM \"people\" t0", sql);
    }

    @Test
    void rendersWherePredicate() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WHERE p.id > 1 AND p.id < 10 RETURN p");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals("SELECT t0.* FROM \"people\" t0 WHERE ((t0.id > 1) AND (t0.id < 10))", sql);
    }

    @Test
    void rendersJoinTableRowsWhenReturningEdgeVariable() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH ()-[r:ACTED_IN]->() RETURN r");
        final String sql = query.asSql(schema).render(new BasicDialect());
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
        final String sql = query.asSql(schema).render(new BasicDialect());
        assertEquals(
                "SELECT t1.author_id, t0.id FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql);
    }

    @Test
    void rendersForeignKeyColumnsWhenReturningEdgeVariable() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[r:AUTHORED]->(m:Movie) RETURN r");
        final String sql = query.asSql(schema).render(new BasicDialect());
        assertEquals(
                "SELECT t1.author_id, t0.id FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql);
    }

    @Test
    void rendersSelfReferentialColumnsForSameEdgeVariableQuery() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH ()-[r:MANAGES]->() RETURN r");
        final String sql = query.asSql(schema).render(new BasicDialect());
        assertEquals(
                "SELECT t0.manager_id, t1.id FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
                sql);
    }

    @Test
    void rendersCountStarProjection() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(*)");
        final String sql = query.asSql(schema).render(new BasicDialect());
        assertEquals(
                "SELECT COUNT(*) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersCountNodeVariableProjection() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(p)");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT COUNT(t0.id) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void parsesUnsupportedFunctionButFailsAtSqlRendering() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN mystery(p.id)");

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals("Function is parsed but not rendered yet: mystery", ex.getMessage());
    }

    @Test
    void rendersArithmeticReturnExpression() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id + m.id AS total");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT (t0.id + t1.id) AS total FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersArithmeticReturnExpressionWithoutAlias() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id + m.id");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT (t0.id + t1.id) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersComparisonAndLogicalReturnExpression() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of(
                "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id > 1 AND m.id < 10 AS matches");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT ((t0.id > 1) AND (t1.id < 10)) AS matches FROM \"people\" t0 "
                        + "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersCaseReturnExpression() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of(
                "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN CASE WHEN p.id > 1 THEN m.id ELSE 0 END AS score");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals(
                "SELECT CASE WHEN (t0.id > 1) THEN t1.id ELSE 0 END AS score FROM \"people\" t0 "
                        + "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersFunctionReturnExpressionWithoutAlias() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN abs(p.id)");
        final String sql = query.asSql(schema).render(new BasicDialect());

        assertEquals("SELECT ABS(t0.id) FROM \"people\" t0", sql);
    }
}
