package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.CypherEdge;
import com.iisaka.cypher2sql.query.cypher.CypherPattern;
import com.iisaka.cypher2sql.query.cypher.CypherQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CypherPatternExtractorTest {
    @Test
    void parsesRightToLeftEdgeDirection() {
        final CypherQuery query = CypherQuery.parse("MATCH (p:Person)<-[:ACTED_IN]-(m:Movie)");
        final List<CypherPattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(CypherEdge.Direction.RIGHT_TO_LEFT, patterns.get(0).edges().get(0).direction());
    }

    @Test
    void parsesUndirectedEdgeDirection() {
        final CypherQuery query = CypherQuery.parse("MATCH (p:Person)-[:ACTED_IN]-(m:Movie)");
        final List<CypherPattern> patterns = query.patterns();

        assertEquals(1, patterns.size());
        assertEquals(CypherEdge.Direction.UNDIRECTED, patterns.get(0).edges().get(0).direction());
    }

    @Test
    void throwsWhenNodeVariableIsMissing() {
        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CypherQuery.parse("MATCH (:Person)-[:ACTED_IN]->(m:Movie)")
        );
        assertEquals("Node pattern missing variable: (:Person)", ex.getMessage());
    }
}
