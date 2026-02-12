package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.sql.BasicSqlDialect;
import com.iisaka.cypher2sql.query.sql.SqlInsert;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlInsertTest {
    @Test
    void throwsBecauseWriteQueriesAreDisabled() {
        final SqlInsert insert = SqlInsert.into("people")
                .value("id", "1")
                .value("name", "'Alice'");

        assertFalse(insert.isEmpty());
        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> insert.render(new BasicSqlDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. SqlInsert is reserved for future enhancement.",
                ex.getMessage());
    }

    @Test
    void throwsBecausePlaceholderIsReadOnlyEvenWithoutValues() {
        final SqlInsert insert = SqlInsert.into("people");
        assertTrue(insert.isEmpty());

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> insert.render(new BasicSqlDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. SqlInsert is reserved for future enhancement.",
                ex.getMessage());
    }
}
