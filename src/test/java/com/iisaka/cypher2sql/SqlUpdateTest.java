package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.sql.BasicDialect;
import com.iisaka.cypher2sql.query.sql.UpdateQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUpdateTest {
    @Test
    void throwsBecauseWriteQueriesAreDisabled() {
        final UpdateQuery update = UpdateQuery.table("people")
                .set("name", "'Bob'")
                .where("id = 1");

        assertTrue(update.hasAssignments());
        assertTrue(update.hasWhereClause());
        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> update.render(new BasicDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. UpdateQuery is reserved for future enhancement.",
                ex.getMessage());
    }
}
