package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.cypher.Expression;
import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.cypher.Syntax;
import com.iisaka.cypher2sql.query.read.ReadQuery;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherPatternExtractorTest {
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
