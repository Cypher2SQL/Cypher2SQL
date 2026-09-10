package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.expression.BinaryExpression;
import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.cypher.expression.Expression;
import com.iisaka.cypher2sql.query.cypher.Pattern;
import com.iisaka.cypher2sql.query.cypher.expression.PropertyExpression;
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
    void rendersWithAliasedScalarProjection() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p.id AS pid WHERE pid > 1 RETURN pid");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT with0.pid FROM (SELECT t0.id AS pid FROM \"people\" t0) with0 WHERE (with0.pid > 1)",
                sql
        );
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
    void rendersStringLiteralConstantContainingASingleQuote() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WHERE p.name = \"O'Brien\" RETURN p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 WHERE (t0.name = 'O''Brien')", sql);
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

        assertEquals(1, query.matchClauses().size());
        assertEquals(Edge.Direction.RIGHT_TO_LEFT, firstPattern(query).edges().get(0).direction());
    }

    @Test
    void parsesUndirectedEdgeDirection() {
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]-(m:Movie) RETURN p, m");

        assertEquals(1, query.matchClauses().size());
        assertEquals(Edge.Direction.UNDIRECTED, firstPattern(query).edges().get(0).direction());
    }

    @Test
    void allowsAnonymousNodeVariable() {
        final Query query = Query.of("MATCH (:Person)-[:ACTED_IN]->(m:Movie) RETURN m");

        assertEquals(1, query.matchClauses().size());
        assertNull(firstPattern(query).nodes().get(0).variable());
        assertEquals("Person", firstPattern(query).nodes().get(0).label());
    }

    @Test
    void parsesSingleNodePattern() {
        final Query query = Query.of("MATCH (p:Person) RETURN p");

        assertEquals(1, query.matchClauses().size());
        assertEquals(1, firstPattern(query).nodes().size());
        assertEquals(0, firstPattern(query).edges().size());
        assertEquals("p", firstPattern(query).nodes().get(0).variable());
    }

    @Test
    void parsesAnonymousNodesAroundRelationship() {
        final Query query = Query.of("MATCH ()-[r:ACTED_IN]->() RETURN r");

        assertEquals(1, query.matchClauses().size());
        assertEquals(2, firstPattern(query).nodes().size());
        assertNull(firstPattern(query).nodes().get(0).variable());
        assertNull(firstPattern(query).nodes().get(1).variable());
        assertEquals(1, firstPattern(query).edges().size());
        assertEquals("r", firstPattern(query).edges().get(0).variable());
        assertEquals("ACTED_IN", firstPattern(query).edges().get(0).type());
        assertEquals(Edge.Direction.LEFT_TO_RIGHT, firstPattern(query).edges().get(0).direction());
    }

    private static Pattern firstPattern(final Query query) {
        return query.matchClauses().get(0).patterns().get(0);
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

        assertTrue(query.matchClauses().get(0).whereExpression() instanceof BinaryExpression);
    }

    @Test
    void parsesWithExpressions() {
        final Query query = Query.of("MATCH (p:Person) WITH p.id AS pid WHERE p.id > 1 RETURN pid");

        assertTrue(query.hasWithClause());
        assertEquals(1, query.withClause().orElseThrow().items().size());
        assertEquals("pid", query.withClause().orElseThrow().items().get(0).alias());
        assertTrue(query.withClause().orElseThrow().items().get(0).expression() instanceof PropertyExpression);
        assertTrue(query.withClause().orElseThrow().whereExpression() instanceof BinaryExpression);
    }

    @Test
    void rendersOrderByAscendingByDefault() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p ORDER BY p.id");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 ORDER BY t0.id ASC", sql);
    }

    @Test
    void rendersOrderByDescending() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p ORDER BY p.id DESC");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 ORDER BY t0.id DESC", sql);
    }

    @Test
    void rendersOrderByMultipleColumnsWithMixedDirection() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p ORDER BY p.name ASC, p.id DESC");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 ORDER BY t0.name ASC, t0.id DESC", sql);
    }

    @Test
    void rendersOrderByOnBareVariable() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p ORDER BY p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 ORDER BY t0.id ASC", sql);
    }

    @Test
    void rendersLimit() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p LIMIT 5");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 LIMIT 5", sql);
    }

    @Test
    void rendersSkipAsUnlimitedOffset() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p SKIP 5");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 LIMIT -1 OFFSET 5", sql);
    }

    @Test
    void rendersLimitWithSkip() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN p SKIP 2 LIMIT 5");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT t0.* FROM \"people\" t0 LIMIT 5 OFFSET 2", sql);
    }

    @Test
    void throwsForNonLiteralLimit() {
        final Exception exception = assertThrows(UnsupportedOperationException.class,
                () -> Query.of("MATCH (p:Person) RETURN p LIMIT p.id"));

        assertEquals("LIMIT must be an integer literal; parameters are not supported yet.", exception.getMessage());
    }

    @Test
    void throwsForNonLiteralSkip() {
        final Exception exception = assertThrows(UnsupportedOperationException.class,
                () -> Query.of("MATCH (p:Person) RETURN p SKIP p.id"));

        assertEquals("SKIP must be an integer literal; parameters are not supported yet.", exception.getMessage());
    }

    @Test
    void rendersOptionalMatchAsLeftJoin() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of(
                "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (m)<-[:AUTHORED]-(a:Person) RETURN p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people_movies\" j3 ON t0.id = j3.person_id "
                        + "INNER JOIN \"movies\" t1 ON j3.movie_id = t1.id "
                        + "LEFT JOIN \"people\" t2 ON t1.author_id = t2.id",
                sql
        );
    }

    @Test
    void returnsVariableBoundOnlyByOptionalMatch() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of(
                "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (m)<-[:AUTHORED]-(a:Person) RETURN a");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT t2.* FROM \"people\" t0 INNER JOIN \"people_movies\" j3 ON t0.id = j3.person_id "
                        + "INNER JOIN \"movies\" t1 ON j3.movie_id = t1.id "
                        + "LEFT JOIN \"people\" t2 ON t1.author_id = t2.id",
                sql
        );
    }

    @Test
    void throwsForOptionalMatchOnUnboundVariable() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of(
                "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (a:Person)-[:AUTHORED]->(other:Movie) RETURN p");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals(
                "OPTIONAL MATCH must reference a variable already bound by a preceding MATCH clause.",
                exception.getMessage());
    }

    @Test
    void throwsForLeadingOptionalMatch() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("OPTIONAL MATCH (p:Person) RETURN p");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals("OPTIONAL MATCH cannot be the first clause yet.", exception.getMessage());
    }

    @Test
    void rendersWhereOnOptionalMatchAsExtraJoinCondition() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of(
                "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) OPTIONAL MATCH (m)<-[:AUTHORED]-(a:Person) "
                        + "WHERE a.id > 1 RETURN p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT t0.* FROM \"people\" t0 INNER JOIN \"people_movies\" j3 ON t0.id = j3.person_id "
                        + "INNER JOIN \"movies\" t1 ON j3.movie_id = t1.id "
                        + "LEFT JOIN \"people\" t2 ON t1.author_id = t2.id AND ((t2.id > 1))",
                sql
        );
    }

    @Test
    void throwsForTwoNonOptionalMatchClauses() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) MATCH (m:Movie) RETURN p, m");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals("Multiple top-level MATCH patterns are not supported yet. Found: 2", exception.getMessage());
    }

    @Test
    void rendersReturnDistinctOnProperty() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN DISTINCT p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT DISTINCT t0.name FROM \"people\" t0 INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id",
                sql
        );
    }

    @Test
    void rendersReturnDistinctOnWildcard() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) RETURN DISTINCT p");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT DISTINCT t0.* FROM \"people\" t0", sql);
    }

    @Test
    void rendersWithBareNodePassthrough() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p RETURN p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals("SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0) with0", sql);
    }

    @Test
    void rendersWithBareNodePassthroughAndPreWithWhere() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WHERE p.id > 1 WITH p RETURN p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0 WHERE (t0.id > 1)) with0",
                sql
        );
    }

    @Test
    void rendersWithWholeQueryAggregateWithoutGrouping() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH count(m) AS total RETURN total");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT with0.total FROM (SELECT COUNT(t1.id) AS total FROM \"people\" t0 "
                        + "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id) with0",
                sql
        );
    }

    @Test
    void rendersPostWithWhereAgainstPassedThroughNode() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p WHERE p.id > 1 RETURN p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0) with0 WHERE (with0.id > 1)",
                sql
        );
    }

    @Test
    void rendersWithDistinct() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH DISTINCT p RETURN p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT with0.name FROM (SELECT DISTINCT t0.* FROM \"people\" t0 "
                        + "INNER JOIN \"people_movies\" j2 ON t0.id = j2.person_id "
                        + "INNER JOIN \"movies\" t1 ON j2.movie_id = t1.id) with0",
                sql
        );
    }

    @Test
    void rendersWithOwnOrderByAndLimitIndependentlyOfReturn() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p ORDER BY p.name LIMIT 1 RETURN p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        assertEquals(
                "SELECT with0.name FROM (SELECT t0.* FROM \"people\" t0 ORDER BY t0.name ASC LIMIT 1) with0",
                sql
        );
    }

    @Test
    void throwsForTwoWithClauses() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p AS p1 WITH p1 AS p2 RETURN p2");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals("Only one WITH clause is supported yet.", exception.getMessage());
    }

    @Test
    void throwsForMatchAfterWith() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p MATCH (p)-[:ACTED_IN]->(m:Movie) RETURN m");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals("MATCH after WITH is not supported yet.", exception.getMessage());
    }

    @Test
    void throwsForMixedAggregationInWith() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH p, count(m) AS total RETURN p, total");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals(
                "Aggregation grouping in WITH is not supported yet; all WITH items must be aggregate expressions, or none.",
                exception.getMessage());
    }

    @Test
    void throwsForUnaliasedComputedWithItem() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person) WITH p.name RETURN p.name");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals("WITH items must be aliased unless they pass through a plain variable.", exception.getMessage());
    }

    @Test
    void throwsForRelationshipVariablePassthroughInWith() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) WITH r RETURN r");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals("Relationship variables cannot be passed through WITH yet: r", exception.getMessage());
    }

    @Test
    void throwsForMoreThanOneNodePassthroughInWith() {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource("schema.yaml");
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH p, m RETURN p");

        final Exception exception = assertThrows(UnsupportedOperationException.class, () -> query.asSql(schema));
        assertEquals(
                "WITH can pass through at most one node variable unchanged; alias the rest to a scalar expression.",
                exception.getMessage());
    }
}
