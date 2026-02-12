package com.iisaka.cypher2sql.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SchemaDefinition {
    private final Map<String, NodeMapping> nodes = new LinkedHashMap<>();
    private final Map<String, EdgeMapping> edges = new LinkedHashMap<>();

    public SchemaDefinition addNode(final NodeMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        nodes.put(mapping.label(), mapping);
        return this;
    }

    public SchemaDefinition addEdge(final EdgeMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        edges.put(mapping.type(), mapping);
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
        final EdgeMapping mapping = edges.get(type);
        if (mapping == null) {
            throw new IllegalArgumentException("No edge mapping for type: " + type);
        }
        return mapping;
    }
}
