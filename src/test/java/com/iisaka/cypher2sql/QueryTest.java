package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.cypher.Expression;
import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.cypher.Syntax;
import com.iisaka.cypher2sql.query.read.ReadQuery;
import com.iisaka.cypher2sql.query.sql.StandardGrammar;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryTest {
    @Test
    void parsesCypherAndRendersSql() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) RETURN p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsLeftNode() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:AUTHORED]->(m:Movie) RETURN p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql
        );
    }

    @Test
    void rendersOneToManyJoinWhenParentIsRightNode() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (m:Movie)-[:AUTHORED]->(p:Person) RETURN m");
        final String sql = query.asSql(schema).render(new StandardGrammar());

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

        final String ltrSql = leftToRight.asSql(schema).render(new StandardGrammar());
        final String rtlSql = rightToLeft.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT t0.*, t1.*, t2.* FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id "
                        + "INNER JOIN \"people\" t2 ON t1.author_id = t2.id",
                sql);
    }

    @Test
    void rendersReturnPropertiesOnly() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id, m.id");
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0", sql);
    }

    @Test
    void rendersWherePredicate() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WHERE p.id > 1 AND p.id < 10 RETURN p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 WHERE ((t0.id > 1) AND (t0.id < 10))", sql);
    }

    @Test
    void rendersJoinTableRowsWhenReturningEdgeVariable() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH ()-[r:ACTED_IN]->() RETURN r");
        final String sql = query.asSql(schema).render(new StandardGrammar());
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
        final String sql = query.asSql(schema).render(new StandardGrammar());
        assertEquals(
                "SELECT t1.author_id, t0.id FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql);
    }

    @Test
    void rendersForeignKeyColumnsWhenReturningEdgeVariable() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[r:AUTHORED]->(m:Movie) RETURN r");
        final String sql = query.asSql(schema).render(new StandardGrammar());
        assertEquals(
                "SELECT t1.author_id, t0.id FROM \"people\" t0 INNER JOIN \"movies\" t1 ON t1.author_id = t0.id",
                sql);
    }

    @Test
    void rendersSelfReferentialColumnsForSameEdgeVariableQuery() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH ()-[r:MANAGES]->() RETURN r");
        final String sql = query.asSql(schema).render(new StandardGrammar());
        assertEquals(
                "SELECT t0.manager_id, t1.id FROM \"people\" t0 INNER JOIN \"people\" t1 ON t0.manager_id = t1.id",
                sql);
    }

    @Test
    void rendersCountStarProjection() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(*)");
        final String sql = query.asSql(schema).render(new StandardGrammar());
        assertEquals(
                "SELECT COUNT(*) FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersCountNodeVariableProjection() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN count(p)");
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT (t0.id + t1.id) AS total FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql);
    }

    @Test
    void rendersArithmeticReturnExpressionWithoutAlias() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p.id + m.id");
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

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
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT ABS(t0.id) FROM \"people\" t0", sql);
    }

    @Test
    void rendersMappedNodeAndRelationshipPropertiesWithQualifiedTablesAndCompositeKeys() {
        final String raw = """
                nodes:
                  - label: Person
                    schema: graph
                    table: people
                    primaryKeys: [tenant_id, id]
                    properties:
                      name: full_name
                  - label: Movie
                    schema: graph
                    table: movies
                    primaryKeys: [tenant_id, id]
                    properties:
                      title: movie_title
                edges:
                  - type: ACTED_IN
                    kind: JOIN_TABLE
                    fromLabel: Person
                    toLabel: Movie
                    joinTable: graph.people_movies
                    fromJoinKeys: [person_tenant_id, person_id]
                    toJoinKeys: [movie_tenant_id, movie_id]
                    properties:
                      role: character_name
                """;
        final SchemaDefinition schema = SchemaDefinition.fromYamlString(raw);
        final Query query = Query.of("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) RETURN p.name, r.role, m.title");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT t0.full_name, j2.character_name, t1.movie_title FROM \"graph\".\"people\" t0 "
                        + "INNER JOIN \"graph\".\"people_movies\" j2 ON t0.tenant_id = j2.person_tenant_id AND t0.id = j2.person_id "
                        + "INNER JOIN \"graph\".\"movies\" t1 ON j2.movie_tenant_id = t1.tenant_id AND j2.movie_id = t1.id",
                sql);
    }

    @Test
    void parsesRightToLeftEdgeDirection() {
        final Query query = Query.of("MATCH (p:Person)<-[:ACTED_IN]-(m:Movie) RETURN p, m");

        assertEquals(1, query.patternCount());
        assertEquals(Edge.Direction.RIGHT_TO_LEFT, query.edgesAt(0).get(0).direction());
    }

    @Test
    void parsesUndirectedEdgeDirection() {
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]-(m:Movie) RETURN p, m");

        assertEquals(1, query.patternCount());
        assertEquals(Edge.Direction.UNDIRECTED, query.edgesAt(0).get(0).direction());
    }

    @Test
    void allowsAnonymousNodeVariable() {
        final Query query = Query.of("MATCH (:Person)-[:ACTED_IN]->(m:Movie) RETURN m");

        assertEquals(1, query.patternCount());
        assertNull(query.nodesAt(0).get(0).variable());
        assertEquals("Person", query.nodesAt(0).get(0).label());
    }

    @Test
    void parsesSingleNodePattern() {
        final Query query = Query.of("MATCH (p:Person) RETURN p");

        assertEquals(1, query.patternCount());
        assertEquals(1, query.nodesAt(0).size());
        assertEquals(0, query.edgesAt(0).size());
        assertEquals("p", query.nodesAt(0).get(0).variable());
    }

    @Test
    void parsesAnonymousNodesAroundRelationship() {
        final Query query = Query.of("MATCH ()-[r:ACTED_IN]->() RETURN r");

        assertEquals(1, query.patternCount());
        assertEquals(2, query.nodesAt(0).size());
        assertNull(query.nodesAt(0).get(0).variable());
        assertNull(query.nodesAt(0).get(1).variable());
        assertEquals(1, query.edgesAt(0).size());
        assertEquals("r", query.edgesAt(0).get(0).variable());
        assertEquals("ACTED_IN", query.edgesAt(0).get(0).type());
        assertEquals(Edge.Direction.LEFT_TO_RIGHT, query.edgesAt(0).get(0).direction());
    }

    @Test
    void antlrParsesCountStarReturnExpression() {
        final var parseTree = Syntax.cypher25().parseTree("MATCH (p:Person) RETURN count(*)");
        final String text = parseTree.getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(text.contains("match"));
        assertTrue(text.contains("return"));
        assertTrue(text.contains("count(*)"));
    }

    @Test
    void bindsToReadQuery() {
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p, m");
        final SchemaDefinition schema = new SchemaDefinition()
                .addNode(new NodeMapping("Person", "people", "id"))
                .addNode(new NodeMapping("Movie", "movies", "id"))
                .addEdge(EdgeMapping.forJoinTable(
                        "ACTED_IN",
                        "Person",
                        "Movie",
                        "people_movies",
                        "person_id",
                        "movie_id"));

        final ReadQuery readQuery = query.asReadQuery(schema);

        assertEquals(1, readQuery.patternCount());
        assertEquals("people", readQuery.patternAt(0).root().mapping().table());
        assertEquals("t0", readQuery.patternAt(0).root().alias());
        assertEquals(1, readQuery.patternAt(0).traversals().size());
    }

    @Test
    void parsesWhereExpression() {
        final Query query = Query.of("MATCH (p:Person) WHERE p.id > 1 AND p.id < 10 RETURN p");

        assertTrue(query.whereExpression() instanceof Expression.BinaryExpression);
    }

    @Test
    void parsesWithExpressions() {
        final Query query = Query.of("MATCH (p:Person) WITH p.id AS pid WHERE p.id > 1 RETURN pid");

        assertTrue(query.hasWithClause());
        assertEquals(1, query.withProjectionItems().size());
        assertEquals("pid", query.withProjectionItems().get(0).alias());
        assertTrue(query.withProjectionItems().get(0).expression() instanceof Expression.PropertyExpression);
        assertTrue(query.withWhereExpression() instanceof Expression.BinaryExpression);
    }
}
