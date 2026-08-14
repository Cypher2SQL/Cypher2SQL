package com.iisaka.cypher2sql.integration;

import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.query.sql.StandardGrammar;
import com.iisaka.cypher2sql.schema.SchemaDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryTest {
    private static final String SCHEMA_RESOURCE = "integration/schema.yaml";
    private static final String DATABASE_RESOURCE = "integration/database.sql";

    @Test
    void executesRenderedSqlAgainstFreshDatabase() throws Exception {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource(SCHEMA_RESOURCE);
        final Query query = Query.of(
                "MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) "
                        + "WHERE p.id = 1 AND m.id = 10 "
                        + "RETURN p.name, r.role, m.title");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSqlResource(connection, DATABASE_RESOURCE);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                assertTrue(resultSet.next());
                assertEquals("Keanu Reeves", resultSet.getString(1));
                assertEquals("Neo", resultSet.getString(2));
                assertEquals("The Matrix", resultSet.getString(3));
                assertFalse(resultSet.next());
            }
        }
    }

    private static void executeSqlResource(final Connection connection, final String resourcePath)
            throws IOException, SQLException {
        final String script;
        try (InputStream input = QueryTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing SQL resource: " + resourcePath);
            }
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Statement statement = connection.createStatement()) {
            for (String rawStatement : script.split(";")) {
                final String sql = rawStatement.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
