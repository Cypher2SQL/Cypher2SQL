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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void executesOrderByLimitAndSkipAgainstFreshDatabase() throws Exception {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource(SCHEMA_RESOURCE);
        final Query query = Query.of("MATCH (p:Person) RETURN p.name ORDER BY p.name SKIP 1 LIMIT 1");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSqlResource(connection, DATABASE_RESOURCE);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                assertTrue(resultSet.next());
                assertEquals("Keanu Reeves", resultSet.getString(1));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void executesOptionalMatchAsOuterJoinAgainstFreshDatabase() throws Exception {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource(SCHEMA_RESOURCE);
        final Query query = Query.of(
                "MATCH (p:Person) OPTIONAL MATCH (p)-[:DIRECTED]->(m:Movie) RETURN p.name, m.title ORDER BY p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSqlResource(connection, DATABASE_RESOURCE);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                assertTrue(resultSet.next());
                assertEquals("Carrie-Anne Moss", resultSet.getString(1));
                assertNull(resultSet.getString(2));

                assertTrue(resultSet.next());
                assertEquals("Keanu Reeves", resultSet.getString(1));
                assertEquals("Speed", resultSet.getString(2));

                assertTrue(resultSet.next());
                assertEquals("Lana Wachowski", resultSet.getString(1));
                assertEquals("The Matrix", resultSet.getString(2));

                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void executesReturnDistinctAgainstFreshDatabase() throws Exception {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource(SCHEMA_RESOURCE);
        final Query query = Query.of("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN DISTINCT p.name ORDER BY p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSqlResource(connection, DATABASE_RESOURCE);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                assertTrue(resultSet.next());
                assertEquals("Carrie-Anne Moss", resultSet.getString(1));

                assertTrue(resultSet.next());
                assertEquals("Keanu Reeves", resultSet.getString(1));

                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void executesOptionalMatchWithOwnWherePreservingOuterJoinSemantics() throws Exception {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource(SCHEMA_RESOURCE);
        final Query query = Query.of(
                "MATCH (p:Person) OPTIONAL MATCH (p)-[:DIRECTED]->(m:Movie) WHERE m.id > 100 "
                        + "RETURN p.name, m.title ORDER BY p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSqlResource(connection, DATABASE_RESOURCE);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                assertTrue(resultSet.next());
                assertEquals("Carrie-Anne Moss", resultSet.getString(1));
                assertNull(resultSet.getString(2));

                assertTrue(resultSet.next());
                assertEquals("Keanu Reeves", resultSet.getString(1));
                assertNull(resultSet.getString(2));

                assertTrue(resultSet.next());
                assertEquals("Lana Wachowski", resultSet.getString(1));
                assertNull(resultSet.getString(2));

                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void executesWithWholeQueryAggregateAndPostWithFilterAgainstFreshDatabase() throws Exception {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource(SCHEMA_RESOURCE);
        final Query query = Query.of(
                "MATCH (p:Person)-[:ACTED_IN]->(m:Movie) WITH count(m) AS totalRoles "
                        + "WHERE totalRoles > 2 RETURN totalRoles");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSqlResource(connection, DATABASE_RESOURCE);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                assertTrue(resultSet.next());
                assertEquals(3, resultSet.getInt(1));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void executesWithPassthroughAndPostWithFilterAgainstFreshDatabase() throws Exception {
        final SchemaDefinition schema = SchemaDefinition.fromYamlResource(SCHEMA_RESOURCE);
        final Query query = Query.of(
                "MATCH (p:Person) WITH p WHERE p.id > 1 RETURN p.name ORDER BY p.name");
        final String sql = query.asSql(schema).render(new StandardGrammar());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            executeSqlResource(connection, DATABASE_RESOURCE);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                assertTrue(resultSet.next());
                assertEquals("Carrie-Anne Moss", resultSet.getString(1));

                assertTrue(resultSet.next());
                assertEquals("Lana Wachowski", resultSet.getString(1));

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
