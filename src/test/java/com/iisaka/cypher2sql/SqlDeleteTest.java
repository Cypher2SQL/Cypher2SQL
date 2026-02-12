package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.sql.BasicDialect;
import com.iisaka.cypher2sql.query.sql.DeleteQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDeleteTest {
    @Test
    void throwsBecauseWriteQueriesAreDisabled() {
        final DeleteQuery delete = DeleteQuery.from("people").where("id = 1");

        assertTrue(delete.hasWhereClause());
        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> delete.render(new BasicDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. DeleteQuery is reserved for future enhancement.",
                ex.getMessage());
    }
}
