package com.iisaka.cypher2sql.schema;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class SchemaDefinitionJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SchemaDefinitionJson() {
    }

    public static SchemaDefinition fromResource(final String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream input = SchemaDefinitionJson.class.getClassLoader().getResourceAsStream(resourcePath)) {
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

    public static SchemaDefinition fromString(final String json) {
        Objects.requireNonNull(json, "json");
        try (InputStream input = new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return fromInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema JSON.", ex);
        }
    }

    private static SchemaDefinition fromInputStream(final InputStream input) throws IOException {
        final SchemaPayload payload = MAPPER.readValue(input, SchemaPayload.class);
        final SchemaDefinition schema = new SchemaDefinition();
        if (payload.nodes() != null) {
            for (final NodePayload node : payload.nodes()) {
                schema.addNode(new NodeMapping(node.label(), node.table(), node.primaryKey()));
            }
        }
        if (payload.edges() != null) {
            for (final EdgePayload edge : payload.edges()) {
                schema.addEdge(edge.toMapping());
            }
        }
        return schema;
    }

    private record SchemaPayload(List<NodePayload> nodes, List<EdgePayload> edges) {
    }

    private record NodePayload(String label, String table, String primaryKey) {
    }

    private record EdgePayload(
            String type,
            EdgeMapping.RelationshipKind kind,
            String fromLabel,
            String toLabel,
            String joinTable,
            String fromJoinKey,
            String toJoinKey,
            String fromKey,
            String toKey,
            String parentLabel,
            String childLabel,
            String parentPrimaryKey,
            String childForeignKey) {

        EdgeMapping toMapping() {
            if (kind == null) {
                throw new IllegalArgumentException("Edge mapping missing kind for type: " + type);
            }
            return switch (kind) {
                case JOIN_TABLE -> EdgeMapping.forJoinTable(
                        type,
                        fromLabel,
                        toLabel,
                        joinTable,
                        fromJoinKey,
                        toJoinKey);
                case SELF_REFERENTIAL -> EdgeMapping.forSelfReferential(
                        type,
                        fromLabel,
                        fromKey,
                        toKey);
                case ONE_TO_MANY -> EdgeMapping.forOneToMany(
                        type,
                        parentLabel,
                        childLabel,
                        parentPrimaryKey,
                        childForeignKey);
            };
        }
    }
}
