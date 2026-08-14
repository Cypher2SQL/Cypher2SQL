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
                schema.addNode(nodeMappingFromPayload(node));
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
                schema.addEdge(edgeMappingFromPayload(edge, kind));
            }
        }

        return schema;
    }

    private static NodeMapping nodeMappingFromPayload(final Map<String, Object> node) {
        final String label = requireString(node, "label", "node");
        return new NodeMapping(
                label,
                stringList(node.get("labels"), List.of(label)),
                stringList(node.get("inherits"), List.of()),
                optionalString(node.get("catalog")),
                optionalString(node.get("schema")),
                requireString(node, "table", "node"),
                stringList(firstPresent(node, "primaryKeys", "primaryKey"), List.of("id")),
                propertyMappings(node.get("properties")),
                nestedStringList(node.get("uniqueKeys")));
    }

    private static EdgeMapping edgeMappingFromPayload(
            final Map<String, Object> edge,
            final EdgeMapping.RelationshipKind kind) {
        final Map<String, PropertyMapping> properties = propertyMappings(edge.get("properties"));
        final EdgeMapping mapping = switch (kind) {
            case JOIN_TABLE -> EdgeMapping.forJoinTable(
                    requireString(edge, "type", "edge"),
                    requireString(edge, "fromLabel", "edge"),
                    requireString(edge, "toLabel", "edge"),
                    requireString(edge, "joinTable", "edge"),
                    stringList(firstPresent(edge, "fromJoinKeys", "fromJoinKey"), List.of()),
                    stringList(firstPresent(edge, "toJoinKeys", "toJoinKey"), List.of()),
                    properties);
            case SELF_REFERENTIAL -> EdgeMapping.forSelfReferential(
                    requireString(edge, "type", "edge"),
                    requireString(firstPresent(edge, "label", "fromLabel"), "edge.label"),
                    stringList(firstPresent(edge, "fromKeys", "fromKey"), List.of()),
                    stringList(firstPresent(edge, "toKeys", "toKey"), List.of()),
                    properties);
            case ONE_TO_MANY -> EdgeMapping.forOneToMany(
                    requireString(edge, "type", "edge"),
                    requireString(edge, "parentLabel", "edge"),
                    requireString(edge, "childLabel", "edge"),
                    stringList(firstPresent(edge, "parentPrimaryKeys", "parentPrimaryKey"), List.of()),
                    stringList(firstPresent(edge, "childForeignKeys", "childForeignKey"), List.of()),
                    properties);
            case MANY_TO_ONE -> EdgeMapping.forManyToOne(
                    requireString(edge, "type", "edge"),
                    requireString(edge, "fromLabel", "edge"),
                    requireString(edge, "toLabel", "edge"),
                    stringList(firstPresent(edge, "fromForeignKeys", "fromForeignKey"), List.of()),
                    stringList(firstPresent(edge, "toPrimaryKeys", "toPrimaryKey"), List.of()),
                    properties);
        };
        final EdgeMapping.Cardinality cardinality = edge.get("cardinality") instanceof String rawCardinality
                ? EdgeMapping.Cardinality.valueOf(rawCardinality)
                : null;
        final boolean unique = edge.get("unique") instanceof Boolean rawUnique && rawUnique;
        return mapping.withMetadata(cardinality, unique);
    }

    private static SchemaDefinition fromJsonInputStream(final InputStream input) throws IOException {
        final Object raw = JSON.readValue(input, Object.class);
        if (!(raw instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Schema JSON must be a mapping.");
        }
        return fromGenericPayload(payload);
    }

    @SuppressWarnings("unchecked")
    private static SchemaDefinition fromGenericPayload(final Map<?, ?> payload) {
        final SchemaDefinition schema = new SchemaDefinition();
        final Object nodesRaw = payload.get("nodes");
        if (nodesRaw instanceof List<?> nodes) {
            for (final Object nodeObj : nodes) {
                if (!(nodeObj instanceof Map<?, ?> nodeRaw)) {
                    throw new IllegalArgumentException("Each node entry must be a mapping.");
                }
                schema.addNode(nodeMappingFromPayload((Map<String, Object>) nodeRaw));
            }
        }

        final Object edgesRaw = payload.get("edges");
        if (edgesRaw instanceof List<?> edges) {
            for (final Object edgeObj : edges) {
                if (!(edgeObj instanceof Map<?, ?> edgeRaw)) {
                    throw new IllegalArgumentException("Each edge entry must be a mapping.");
                }
                final Map<String, Object> edge = (Map<String, Object>) edgeRaw;
                if (!(edge.get("kind") instanceof String kindRaw) || kindRaw.isBlank()) {
                    throw new IllegalArgumentException("Edge mapping missing kind for type: " + edge.get("type"));
                }
                final EdgeMapping.RelationshipKind kind = EdgeMapping.RelationshipKind.valueOf(kindRaw);
                schema.addEdge(edgeMappingFromPayload(edge, kind));
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

    private static String requireString(final Object value, final String fieldName) {
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("Schema JSON missing required string field: " + fieldName);
        }
        return stringValue;
    }

    private static String optionalString(final Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private static Object firstPresent(final Map<String, Object> payload, final String primaryKey, final String fallbackKey) {
        return payload.containsKey(primaryKey) ? payload.get(primaryKey) : payload.get(fallbackKey);
    }

    private static List<String> stringList(final Object raw, final List<String> defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof String value) {
            return List.of(value);
        }
        if (raw instanceof List<?> values) {
            return values.stream()
                    .map(value -> {
                        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
                            throw new IllegalArgumentException("Schema list values must be non-blank strings.");
                        }
                        return stringValue;
                    })
                    .toList();
        }
        throw new IllegalArgumentException("Schema value must be a string or list of strings.");
    }

    private static List<List<String>> nestedStringList(final Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("Schema uniqueKeys must be a list.");
        }
        return values.stream().map(value -> stringList(value, List.of())).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PropertyMapping> propertyMappings(final Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Schema properties must be a mapping.");
        }
        final Map<String, PropertyMapping> properties = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String property) || property.isBlank()) {
                throw new IllegalArgumentException("Schema property names must be non-blank strings.");
            }
            final Object value = entry.getValue();
            if (value instanceof String column) {
                properties.put(property, PropertyMapping.of(property, column));
                continue;
            }
            if (value instanceof Map<?, ?> mappingRaw) {
                final Map<String, Object> mapping = (Map<String, Object>) mappingRaw;
                properties.put(property, new PropertyMapping(
                        property,
                        requireString(mapping, "column", "property"),
                        optionalString(mapping.get("type")),
                        !(mapping.get("nullable") instanceof Boolean nullable) || nullable,
                        mapping.get("quote") instanceof Boolean quote && quote));
                continue;
            }
            throw new IllegalArgumentException("Schema property mapping must be a string or mapping.");
        }
        return properties;
    }
}
