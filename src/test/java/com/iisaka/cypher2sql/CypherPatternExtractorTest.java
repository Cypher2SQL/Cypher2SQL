package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.cypher.Pattern;
import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.cypher.Syntax;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherPatternExtractorTest {
    @Test
    void parsesRightToLeftEdgeDirection() {
        final Query query = Query.of("MATCH (p:Person)<-[:ACTED_IN]-(m:Movie) RETURN p, m");
        final List<Pattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(Edge.Direction.RIGHT_TO_LEFT, patterns.get(0).edges().get(0).direction());
    }

    @Test
    void parsesUndirectedEdgeDirection() {
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]-(m:Movie) RETURN p, m");
        final List<Pattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(Edge.Direction.UNDIRECTED, patterns.get(0).edges().get(0).direction());
    }

    @Test
    void allowsAnonymousNodeVariable() {
        final Query query = Query.of("MATCH (:Person)-[:ACTED_IN]->(m:Movie) RETURN m");
        final List<Pattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertNull(patterns.get(0).nodes().get(0).variable());
        assertEquals("Person", patterns.get(0).nodes().get(0).label());
    }

    @Test
    void parsesSingleNodePattern() {
        final Query query = Query.of("MATCH (p:Person) RETURN p");
        final List<Pattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(1, patterns.get(0).nodes().size());
        assertEquals(0, patterns.get(0).edges().size());
        assertEquals("p", patterns.get(0).nodes().get(0).variable());
    }

    @Test
    void parsesAnonymousNodesAroundRelationship() {
        final Query query = Query.of("MATCH ()-[r:ACTED_IN]->() RETURN r");
        final List<Pattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(2, patterns.get(0).nodes().size());
        assertNull(patterns.get(0).nodes().get(0).variable());
        assertNull(patterns.get(0).nodes().get(1).variable());
        assertEquals(1, patterns.get(0).edges().size());
        assertEquals("r", patterns.get(0).edges().get(0).variable());
        assertEquals("ACTED_IN", patterns.get(0).edges().get(0).type());
        assertEquals(Edge.Direction.LEFT_TO_RIGHT, patterns.get(0).edges().get(0).direction());
    }

    @Test
    void antlrParsesCountStarReturnExpression() {
        final var parseTree = Syntax.cypher25().parseTree("MATCH (p:Person) RETURN count(*)");
        final String text = parseTree.getText().replaceAll("\\s+", "").toLowerCase();
        assertTrue(text.contains("match"));
        assertTrue(text.contains("return"));
        assertTrue(text.contains("count(*)"));
    }
}
