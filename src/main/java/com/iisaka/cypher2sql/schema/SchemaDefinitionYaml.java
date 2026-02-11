package com.iisaka.cypher2sql.schema;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SchemaDefinitionYaml {
    private static final Yaml YAML = new Yaml();

    private SchemaDefinitionYaml() {
    }

    public static SchemaDefinition fromResource(final String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream input = SchemaDefinitionYaml.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return fromInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema resource: " + resourcePath, ex);
        }
    }

    public static SchemaDefinition fromPath(final Path path) {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return fromInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema file: " + path, ex);
        }
    }

    public static SchemaDefinition fromString(final String yaml) {
        Objects.requireNonNull(yaml, "yaml");
        try (InputStream input = new java.io.ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) {
            return fromInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema YAML.", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static SchemaDefinition fromInputStream(final InputStream input) throws IOException {
        final Object raw = YAML.load(input);
        if (!(raw instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Schema YAML must be a mapping.");
        }
        final SchemaDefinition schema = new SchemaDefinition();

        final Object nodesRaw = payload.get("nodes");
        if (nodesRaw instanceof List<?> nodes) {
            for (final Object nodeObj : nodes) {
                final Map<String, Object> node = (Map<String, Object>) nodeObj;
                schema.addNode(new NodeMapping(
                        (String) node.get("label"),
                        (String) node.get("table"),
                        (String) node.get("primaryKey")));
            }
        }

        final Object edgesRaw = payload.get("edges");
        if (edgesRaw instanceof List<?> edges) {
            for (final Object edgeObj : edges) {
                final Map<String, Object> edge = (Map<String, Object>) edgeObj;
                final EdgeMapping.RelationshipKind kind = EdgeMapping.RelationshipKind.valueOf((String) edge.get("kind"));
                schema.addEdge(edgeMappingFromPayload(edge, kind));
            }
        }

        return schema;
    }

    private static EdgeMapping edgeMappingFromPayload(
            final Map<String, Object> edge,
            final EdgeMapping.RelationshipKind kind) {
        return switch (kind) {
            case JOIN_TABLE -> EdgeMapping.forJoinTable(
                    (String) edge.get("type"),
                    (String) edge.get("fromLabel"),
                    (String) edge.get("toLabel"),
                    (String) edge.get("joinTable"),
                    (String) edge.get("fromJoinKey"),
                    (String) edge.get("toJoinKey"));
            case SELF_REFERENTIAL -> EdgeMapping.forSelfReferential(
                    (String) edge.get("type"),
                    (String) edge.get("label"),
                    (String) edge.get("fromKey"),
                    (String) edge.get("toKey"));
            case ONE_TO_MANY -> EdgeMapping.forOneToMany(
                    (String) edge.get("type"),
                    (String) edge.get("parentLabel"),
                    (String) edge.get("childLabel"),
                    (String) edge.get("parentPrimaryKey"),
                    (String) edge.get("childForeignKey"));
        };
    }
}
