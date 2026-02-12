package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.sql.BasicDialect;
import com.iisaka.cypher2sql.query.sql.InsertQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlInsertTest {
    @Test
    void throwsBecauseWriteQueriesAreDisabled() {
        final InsertQuery insert = InsertQuery.into("people")
                .value("id", "1")
                .value("name", "'Alice'");

        assertFalse(insert.isEmpty());
        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> insert.render(new BasicDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. InsertQuery is reserved for future enhancement.",
                ex.getMessage());
    }

    @Test
    void throwsBecausePlaceholderIsReadOnlyEvenWithoutValues() {
        final InsertQuery insert = InsertQuery.into("people");
        assertTrue(insert.isEmpty());

        final UnsupportedOperationException ex =
                assertThrows(UnsupportedOperationException.class, () -> insert.render(new BasicDialect()));
        assertEquals(
                "Write queries are disabled in read-only mode. InsertQuery is reserved for future enhancement.",
                ex.getMessage());
    }
}
