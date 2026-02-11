package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.sql.BasicSqlDialect;
import com.iisaka.cypher2sql.query.sql.SqlUpdate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUpdateTest {
    @Test
    void throwsBecauseWriteQueriesAreDisabled() {
        final SqlUpdate update = SqlUpdate.table("people")
                .set("name", "'Bob'")
                .where("id = 1");

        assertTrue(update.hasAssignments());
        assertTrue(update.hasWhereClause());
        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> update.render(new BasicSqlDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. SqlUpdate is reserved for future enhancement.",
                ex.getMessage());
    }
}
