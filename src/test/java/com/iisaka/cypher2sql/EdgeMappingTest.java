package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.PropertyMapping;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeMappingTest {
    @Test
    void selfReferentialScalarFactoryMatchesListFactory() {
        final EdgeMapping mapping = EdgeMapping.forSelfReferential("MANAGES", "Person", "manager_id", "id");

        assertEquals(EdgeMapping.RelationshipKind.SELF_REFERENTIAL, mapping.relationshipKind());
        assertEquals("manager_id", mapping.fromKey());
        assertEquals("id", mapping.toKey());
        assertEquals("Person", mapping.fromLabel());
        assertEquals("Person", mapping.toLabel());
    }

    @Test
    void manyToOneFactoryBuildsManyToOneCardinality() {
        final EdgeMapping mapping = EdgeMapping.forManyToOne(
                "AUTHORED", "Movie", "Person", List.of("author_id"), List.of("id"), Map.of());

        assertEquals(EdgeMapping.RelationshipKind.MANY_TO_ONE, mapping.relationshipKind());
        assertEquals(EdgeMapping.Cardinality.MANY_TO_ONE, mapping.cardinality());
        assertEquals(List.of("author_id"), mapping.childForeignKeys());
        assertEquals(List.of("id"), mapping.parentPrimaryKeys());
    }

    @Test
    void withMetadataOverridesCardinalityAndUniqueFlag() {
        final EdgeMapping base = EdgeMapping.forOneToMany("AUTHORED", "Person", "Movie", "id", "author_id");

        final EdgeMapping updated = base.withMetadata(EdgeMapping.Cardinality.ONE_TO_ONE, true);

        assertEquals(EdgeMapping.Cardinality.ONE_TO_ONE, updated.cardinality());
        assertTrue(updated.unique());
        assertFalse(base.unique());
    }

    @Test
    void withMetadataKeepsExistingCardinalityWhenNullPassed() {
        final EdgeMapping base = EdgeMapping.forOneToMany("AUTHORED", "Person", "Movie", "id", "author_id");

        final EdgeMapping updated = base.withMetadata(null, true);

        assertEquals(base.cardinality(), updated.cardinality());
    }

    @Test
    void scalarAccessorThrowsForCompositeJoinKeys() {
        final EdgeMapping mapping = EdgeMapping.forJoinTable(
                "ACTED_IN", "Person", "Movie", "people_movies",
                List.of("tenant_id", "person_id"), List.of("movie_id"), Map.of());

        final IllegalStateException ex = assertThrows(IllegalStateException.class, mapping::fromJoinKey);
        assertEquals("Composite fromJoinKey is not scalar.", ex.getMessage());
    }

    @Test
    void columnForPropertyThrowsWhenPropertyIsNotMapped() {
        final EdgeMapping mapping = EdgeMapping.forJoinTable(
                "ACTED_IN", "Person", "Movie", "people_movies", "person_id", "movie_id");

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapping.columnForProperty("role"));
        assertEquals("No relationship property mapping for property: role", ex.getMessage());
    }

    @Test
    void columnForPropertyReturnsMappedColumn() {
        final EdgeMapping mapping = EdgeMapping.forJoinTable(
                "ACTED_IN", "Person", "Movie", "people_movies",
                List.of("person_id"), List.of("movie_id"),
                Map.of("role", PropertyMapping.of("role", "role_name")));

        assertEquals("role_name", mapping.columnForProperty("role"));
    }

    @Test
    void matchesUndirectedLabelsAcceptsEitherDirection() {
        final EdgeMapping mapping = EdgeMapping.forJoinTable(
                "ACTED_IN", "Person", "Movie", "people_movies", "person_id", "movie_id");

        assertTrue(mapping.matchesUndirectedLabels("Person", "Movie"));
        assertTrue(mapping.matchesUndirectedLabels("Movie", "Person"));
        assertFalse(mapping.matchesUndirectedLabels("Person", "Person"));
    }

    @Test
    void isLeftParentAndIsRightParentReflectDirectedLabels() {
        final EdgeMapping mapping = EdgeMapping.forOneToMany("AUTHORED", "Person", "Movie", "id", "author_id");

        assertTrue(mapping.isLeftParent("Person", "Movie"));
        assertFalse(mapping.isLeftParent("Movie", "Person"));
        assertTrue(mapping.isRightParent("Movie", "Person"));
        assertFalse(mapping.isRightParent("Person", "Movie"));
    }
}
