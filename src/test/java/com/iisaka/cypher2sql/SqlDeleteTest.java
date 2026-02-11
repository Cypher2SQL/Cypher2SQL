package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.sql.BasicSqlDialect;
import com.iisaka.cypher2sql.query.sql.SqlDelete;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDeleteTest {
    @Test
    void throwsBecauseWriteQueriesAreDisabled() {
        final SqlDelete delete = SqlDelete.from("people").where("id = 1");

        assertTrue(delete.hasWhereClause());
        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> delete.render(new BasicSqlDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. SqlDelete is reserved for future enhancement.",
                ex.getMessage());
    }
}
