package com.iisaka.cypher2sql.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SchemaDefinition {
    private static final Yaml YAML = new Yaml();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, NodeMapping> nodes = new LinkedHashMap<>();
    private final Map<String, EdgeMapping> edgesByKey = new LinkedHashMap<>();
    private final Map<String, java.util.List<EdgeMapping>> edgesByType = new LinkedHashMap<>();

    public SchemaDefinition addNode(final NodeMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        nodes.put(mapping.label(), mapping);
        return this;
    }

    public SchemaDefinition addEdge(final EdgeMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        final String key = edgeKey(mapping.type(), mapping.fromLabel(), mapping.toLabel());
        edgesByKey.put(key, mapping);
        edgesByType.computeIfAbsent(mapping.type(), ignored -> new java.util.ArrayList<>()).add(mapping);
        return this;
    }

    public NodeMapping nodeForLabel(final String label) {
        final NodeMapping mapping = nodes.get(label);
        if (mapping == null) {
            throw new IllegalArgumentException("No node mapping for label: " + label);
        }
        return mapping;
    }

    public EdgeMapping edgeForType(final String type) {
        final java.util.List<EdgeMapping> mappings = edgesByType.get(type);
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("No edge mapping for type: " + type);
        }
        if (mappings.size() > 1) {
            throw new IllegalArgumentException("Ambiguous edge mapping for type: " + type
                    + ". Available pairs: " + mappings.stream()
                    .map(m -> m.fromLabel() + "->" + m.toLabel())
                    .collect(Collectors.joining(", ")));
        }
        return mappings.get(0);
    }

    public EdgeMapping edgeForType(final String type, final String fromLabel, final String toLabel) {
        final EdgeMapping mapping = edgesByKey.get(edgeKey(type, fromLabel, toLabel));
        if (mapping == null) {
            throw new IllegalArgumentException(
                    "No edge mapping for type/labels: " + type + " (" + fromLabel + "->" + toLabel + ")");
        }
        return mapping;
    }

    public EdgeMapping edgeForTypeUndirected(final String type, final String leftLabel, final String rightLabel) {
        final EdgeMapping forward = edgesByKey.get(edgeKey(type, leftLabel, rightLabel));
        final EdgeMapping reverse = edgesByKey.get(edgeKey(type, rightLabel, leftLabel));
        if (forward != null && reverse != null && forward != reverse) {
            throw new IllegalArgumentException(
                    "Ambiguous undirected edge mapping for type/labels: " + type
                            + " (" + leftLabel + "<->" + rightLabel + ")");
        }
        if (forward != null) {
            return forward;
        }
        if (reverse != null) {
            return reverse;
        }
        throw new IllegalArgumentException(
                "No edge mapping for undirected type/labels: " + type + " (" + leftLabel + "<->" + rightLabel + ")");
    }

    public static SchemaDefinition fromYamlResource(final String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream input = SchemaDefinition.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return fromYamlInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema resource: " + resourcePath, ex);
        }
    }

    public static SchemaDefinition fromYamlPath(final Path path) {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return fromYamlInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema file: " + path, ex);
        }
    }

    public static SchemaDefinition fromYamlString(final String yaml) {
        Objects.requireNonNull(yaml, "yaml");
        try (InputStream input = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) {
            return fromYamlInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema YAML.", ex);
        }
    }

    public static SchemaDefinition fromJsonResource(final String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream input = SchemaDefinition.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return fromJsonInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema resource: " + resourcePath, ex);
        }
    }

    public static SchemaDefinition fromJsonPath(final Path path) {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return fromJsonInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema file: " + path, ex);
        }
    }

    public static SchemaDefinition fromJsonString(final String json) {
        Objects.requireNonNull(json, "json");
        try (InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return fromJsonInputStream(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read schema JSON.", ex);
        }
    }

    private String edgeKey(final String type, final String fromLabel, final String toLabel) {
        return type + "|" + fromLabel + "|" + toLabel;
    }

    @SuppressWarnings("unchecked")
    private static SchemaDefinition fromYamlInputStream(final InputStream input) throws IOException {
        final Object raw = YAML.load(input);
        if (!(raw instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Schema YAML must be a mapping.");
        }
        final SchemaDefinition schema = new SchemaDefinition();

        final Object nodesRaw = payload.get("nodes");
        if (nodesRaw instanceof List<?> nodes) {
            for (final Object nodeObj : nodes) {
                if (!(nodeObj instanceof Map<?, ?> nodeRaw)) {
                    throw new IllegalArgumentException("Each node entry must be a mapping.");
                }
                final Map<String, Object> node = (Map<String, Object>) nodeRaw;
                schema.addNode(new NodeMapping(
                        requireString(node, "label", "node"),
                        requireString(node, "table", "node"),
                        requireString(node, "primaryKey", "node")));
            }
        }

        final Object edgesRaw = payload.get("edges");
        if (edgesRaw instanceof List<?> edges) {
            for (final Object edgeObj : edges) {
                if (!(edgeObj instanceof Map<?, ?> edgeRaw)) {
                    throw new IllegalArgumentException("Each edge entry must be a mapping.");
                }
                final Map<String, Object> edge = (Map<String, Object>) edgeRaw;
                final EdgeMapping.RelationshipKind kind = EdgeMapping.RelationshipKind.valueOf(
                        requireString(edge, "kind", "edge"));
                schema.addEdge(yamlEdgeMappingFromPayload(edge, kind));
            }
        }

        return schema;
    }

    private static EdgeMapping yamlEdgeMappingFromPayload(
            final Map<String, Object> edge,
            final EdgeMapping.RelationshipKind kind) {
        return switch (kind) {
            case JOIN_TABLE -> EdgeMapping.forJoinTable(
                    requireString(edge, "type", "edge"),
                    requireString(edge, "fromLabel", "edge"),
                    requireString(edge, "toLabel", "edge"),
                    requireString(edge, "joinTable", "edge"),
                    requireString(edge, "fromJoinKey", "edge"),
                    requireString(edge, "toJoinKey", "edge"));
            case SELF_REFERENTIAL -> EdgeMapping.forSelfReferential(
                    requireString(edge, "type", "edge"),
                    requireString(edge, "label", "edge"),
                    requireString(edge, "fromKey", "edge"),
                    requireString(edge, "toKey", "edge"));
            case ONE_TO_MANY -> EdgeMapping.forOneToMany(
                    requireString(edge, "type", "edge"),
                    requireString(edge, "parentLabel", "edge"),
                    requireString(edge, "childLabel", "edge"),
                    requireString(edge, "parentPrimaryKey", "edge"),
                    requireString(edge, "childForeignKey", "edge"));
        };
    }

    private static SchemaDefinition fromJsonInputStream(final InputStream input) throws IOException {
        final SchemaPayload payload = JSON.readValue(input, SchemaPayload.class);
        final SchemaDefinition schema = new SchemaDefinition();
        if (payload.nodes() != null) {
            for (final NodePayload node : payload.nodes()) {
                schema.addNode(new NodeMapping(
                        requireString(node.label(), "node.label"),
                        requireString(node.table(), "node.table"),
                        requireString(node.primaryKey(), "node.primaryKey")));
            }
        }
        if (payload.edges() != null) {
            for (final EdgePayload edge : payload.edges()) {
                schema.addEdge(edge.asMapping());
            }
        }
        return schema;
    }

    private static String requireString(final Map<String, Object> payload, final String key, final String context) {
        final Object value = payload.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("Schema " + context + " missing required string field: " + key);
        }
        return stringValue;
    }

    private static String requireString(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Schema JSON missing required string field: " + fieldName);
        }
        return value;
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

        EdgeMapping asMapping() {
            if (kind == null) {
                throw new IllegalArgumentException("Edge mapping missing kind for type: " + type);
            }
            return switch (kind) {
                case JOIN_TABLE -> EdgeMapping.forJoinTable(
                        requireString(type, "edge.type"),
                        requireString(fromLabel, "edge.fromLabel"),
                        requireString(toLabel, "edge.toLabel"),
                        requireString(joinTable, "edge.joinTable"),
                        requireString(fromJoinKey, "edge.fromJoinKey"),
                        requireString(toJoinKey, "edge.toJoinKey"));
                case SELF_REFERENTIAL -> EdgeMapping.forSelfReferential(
                        requireString(type, "edge.type"),
                        requireString(fromLabel, "edge.fromLabel"),
                        requireString(fromKey, "edge.fromKey"),
                        requireString(toKey, "edge.toKey"));
                case ONE_TO_MANY -> EdgeMapping.forOneToMany(
                        requireString(type, "edge.type"),
                        requireString(parentLabel, "edge.parentLabel"),
                        requireString(childLabel, "edge.childLabel"),
                        requireString(parentPrimaryKey, "edge.parentPrimaryKey"),
                        requireString(childForeignKey, "edge.childForeignKey"));
            };
        }
    }
}
