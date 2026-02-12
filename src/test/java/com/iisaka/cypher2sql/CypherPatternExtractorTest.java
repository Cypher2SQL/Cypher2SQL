package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.Edge;
import com.iisaka.cypher2sql.query.cypher.Pattern;
import com.iisaka.cypher2sql.query.cypher.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CypherPatternExtractorTest {
    @Test
    void parsesRightToLeftEdgeDirection() {
        final Query query = Query.parse("MATCH (p:Person)<-[:ACTED_IN]-(m:Movie) RETURN p, m");
        final List<Pattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(Edge.Direction.RIGHT_TO_LEFT, patterns.get(0).edges().get(0).direction());
    }

    @Test
    void parsesUndirectedEdgeDirection() {
        final Query query = Query.parse("MATCH (p:Person)-[:ACTED_IN]-(m:Movie) RETURN p, m");
        final List<Pattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(Edge.Direction.UNDIRECTED, patterns.get(0).edges().get(0).direction());
    }

    @Test
    void throwsWhenNodeVariableIsMissing() {
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Query.parse("MATCH (:Person)-[:ACTED_IN]->(m:Movie) RETURN m")
        );
        assertEquals("Node pattern missing variable: (:Person)", ex.getMessage());
    }
}
