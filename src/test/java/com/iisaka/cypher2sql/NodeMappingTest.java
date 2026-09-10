package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.PropertyMapping;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeMappingTest {
    @Test
    void appliesDefaultsWhenOptionalFieldsAreNullOrBlank() {
        final NodeMapping mapping = new NodeMapping(
                "Person", null, null, "", "  ", "people", null, null, null);

        assertEquals(List.of("Person"), mapping.labels());
        assertEquals(List.of(), mapping.inherits());
        assertEquals(null, mapping.catalog());
        assertEquals(null, mapping.schema());
        assertEquals(List.of("id"), mapping.primaryKeys());
        assertEquals(Map.of(), mapping.properties());
        assertEquals(List.of(), mapping.uniqueKeys());
    }

    @Test
    void threeArgConstructorMatchesFullConstructorDefaults() {
        final NodeMapping mapping = new NodeMapping("Person", "people", "id");

        assertEquals("Person", mapping.label());
        assertEquals(List.of("Person"), mapping.labels());
        assertEquals(List.of("id"), mapping.primaryKeys());
        assertEquals("people", mapping.table());
    }

    @Test
    void requiresNonBlankLabel() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new NodeMapping(" ", "people", "id"));
        assertEquals("Node mapping missing required field: label", ex.getMessage());
    }

    @Test
    void requiresNonBlankTable() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new NodeMapping("Person", "", "id"));
        assertEquals("Node mapping missing required field: table", ex.getMessage());
    }

    @Test
    void qualifiesTableWithCatalogAndSchemaWhenPresent() {
        final NodeMapping mapping = new NodeMapping(
                "Person", List.of("Person"), List.of(), "warehouse", "graph", "people",
                List.of("id"), Map.of(), List.of());

        assertEquals("warehouse.graph.people", mapping.table());
    }

    @Test
    void primaryKeyThrowsForCompositeKeys() {
        final NodeMapping mapping = new NodeMapping(
                "Person", List.of("Person"), List.of(), null, null, "people",
                List.of("tenant_id", "id"), Map.of(), List.of());

        final IllegalStateException ex = assertThrows(IllegalStateException.class, mapping::primaryKey);
        assertEquals("Composite primary key is not scalar for label: Person", ex.getMessage());
    }

    @Test
    void matchesLabelChecksLabelsAndInherits() {
        final NodeMapping mapping = new NodeMapping(
                "Employee", List.of("Employee", "Staff"), List.of("Person"), null, null, "employees",
                List.of("id"), Map.of(), List.of());

        assertTrue(mapping.matchesLabel("Staff"));
        assertTrue(mapping.matchesLabel("Person"));
        assertTrue(!mapping.matchesLabel("Movie"));
    }

    @Test
    void requirePropertyNameRejectsBlankProperty() {
        final NodeMapping mapping = new NodeMapping("Person", "people", "id");

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapping.requirePropertyName(" "));
        assertEquals("Property name must be non-blank.", ex.getMessage());
    }

    @Test
    void columnForPropertyFallsBackToPropertyNameWithoutMapping() {
        final NodeMapping mapping = new NodeMapping("Person", "people", "id");

        assertEquals("name", mapping.columnForProperty("name"));
    }

    @Test
    void columnForPropertyUsesMappedColumnWhenPresent() {
        final NodeMapping mapping = new NodeMapping(
                "Person", List.of("Person"), List.of(), null, null, "people",
                List.of("id"), Map.of("fullName", PropertyMapping.of("fullName", "full_name")), List.of());

        assertEquals("full_name", mapping.columnForProperty("fullName"));
    }

    @Test
    void joinOnColumnsThrowsOnArityMismatch() {
        final NodeMapping mapping = new NodeMapping("Person", "people", "id");

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapping.joinOnColumns("t0", List.of("a", "b"), "t1", List.of("c")));
        assertEquals("Join key arity mismatch: 2 != 1", ex.getMessage());
    }

    @Test
    void joinOnColumnsBuildsQualifiedEqualityClauses() {
        final NodeMapping mapping = new NodeMapping("Person", "people", "id");

        assertEquals(
                List.of("t0.a = t1.c", "t0.b = t1.d"),
                mapping.joinOnColumns("t0", List.of("a", "b"), "t1", List.of("c", "d")));
    }

    @Test
    void qualifiedPrimaryKeysMapsEachCompositeKeyColumn() {
        final NodeMapping mapping = new NodeMapping(
                "Person", List.of("Person"), List.of(), null, null, "people",
                List.of("tenant_id", "id"), Map.of(), List.of());

        assertEquals(List.of("t0.tenant_id", "t0.id"), mapping.qualifiedPrimaryKeys("t0"));
    }

    @Test
    void propertyMappingRejectsBlankProperty() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PropertyMapping(" ", "column", null, true, false));
        assertEquals("Property name must be non-blank.", ex.getMessage());
    }

    @Test
    void propertyMappingRejectsBlankColumn() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PropertyMapping("property", " ", null, true, false));
        assertEquals("Column name must be non-blank.", ex.getMessage());
    }
}
