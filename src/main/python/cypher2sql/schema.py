from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
from typing import Any, Dict, Iterable

try:
    import yaml
except ImportError:  # pragma: no cover - runtime dependency
    yaml = None


@dataclass(frozen=True)
class NodeMapping:
    label: str
    table: str
    primary_key: str


class RelationshipKind(Enum):
    JOIN_TABLE = "JOIN_TABLE"
    SELF_REFERENTIAL = "SELF_REFERENTIAL"
    ONE_TO_MANY = "ONE_TO_MANY"


@dataclass(frozen=True)
class EdgeMapping:
    type: str
    from_label: str
    to_label: str
    relationship_kind: RelationshipKind
    join_table: str | None = None
    from_join_key: str | None = None
    to_join_key: str | None = None
    from_key: str | None = None
    to_key: str | None = None
    parent_primary_key: str | None = None
    child_foreign_key: str | None = None

    @classmethod
    def for_join_table(
        cls,
        type: str,
        from_label: str,
        to_label: str,
        join_table: str,
        from_join_key: str,
        to_join_key: str,
    ) -> "EdgeMapping":
        return cls(
            type=type,
            from_label=from_label,
            to_label=to_label,
            relationship_kind=RelationshipKind.JOIN_TABLE,
            join_table=join_table,
            from_join_key=from_join_key,
            to_join_key=to_join_key,
        )

    @classmethod
    def for_self_referential(
        cls,
        type: str,
        label: str,
        from_key: str,
        to_key: str,
    ) -> "EdgeMapping":
        return cls(
            type=type,
            from_label=label,
            to_label=label,
            relationship_kind=RelationshipKind.SELF_REFERENTIAL,
            from_key=from_key,
            to_key=to_key,
        )

    @classmethod
    def for_one_to_many(
        cls,
        type: str,
        parent_label: str,
        child_label: str,
        parent_primary_key: str,
        child_foreign_key: str,
    ) -> "EdgeMapping":
        return cls(
            type=type,
            from_label=parent_label,
            to_label=child_label,
            relationship_kind=RelationshipKind.ONE_TO_MANY,
            parent_primary_key=parent_primary_key,
            child_foreign_key=child_foreign_key,
        )


class SchemaDefinition:
    def __init__(self) -> None:
        self._nodes: Dict[str, NodeMapping] = {}
        self._edges: Dict[str, EdgeMapping] = {}

    def add_node(self, mapping: NodeMapping) -> "SchemaDefinition":
        self._nodes[mapping.label] = mapping
        return self

    def add_edge(self, mapping: EdgeMapping) -> "SchemaDefinition":
        self._edges[mapping.type] = mapping
        return self

    def node_for_label(self, label: str) -> NodeMapping:
        if label not in self._nodes:
            raise ValueError(f"No node mapping for label: {label}")
        return self._nodes[label]

    def edge_for_type(self, type: str) -> EdgeMapping:
        if type not in self._edges:
            raise ValueError(f"No edge mapping for type: {type}")
        return self._edges[type]

    @classmethod
    def from_json_string(cls, raw: str) -> "SchemaDefinition":
        return cls.from_dict(json.loads(raw))

    @classmethod
    def from_json_path(cls, path: str | Path) -> "SchemaDefinition":
        return cls.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))

    @classmethod
    def from_dict(cls, payload: dict) -> "SchemaDefinition":
        schema = cls()
        for node in payload.get("nodes", []):
            schema.add_node(
                NodeMapping(
                    label=node["label"],
                    table=node["table"],
                    primary_key=node["primaryKey"],
                )
            )
        for edge in payload.get("edges", []):
            kind = RelationshipKind(edge["kind"])
            schema.add_edge(_edge_mapping_from_payload(edge, kind))
        return schema

    @classmethod
    def from_yaml_string(cls, raw: str) -> "SchemaDefinition":
        return cls.from_dict(_yaml_load(raw))

    @classmethod
    def from_yaml_path(cls, path: str | Path) -> "SchemaDefinition":
        return cls.from_dict(_yaml_load(Path(path).read_text(encoding="utf-8")))


def _edge_mapping_from_payload(edge: dict, kind: RelationshipKind) -> EdgeMapping:
    if kind is RelationshipKind.JOIN_TABLE:
        return EdgeMapping.for_join_table(
            edge["type"],
            edge["fromLabel"],
            edge["toLabel"],
            edge["joinTable"],
            edge["fromJoinKey"],
            edge["toJoinKey"],
        )
    if kind is RelationshipKind.SELF_REFERENTIAL:
        return EdgeMapping.for_self_referential(
            edge["type"],
            edge["label"],
            edge["fromKey"],
            edge["toKey"],
        )
    if kind is RelationshipKind.ONE_TO_MANY:
        return EdgeMapping.for_one_to_many(
            edge["type"],
            edge["parentLabel"],
            edge["childLabel"],
            edge["parentPrimaryKey"],
            edge["childForeignKey"],
        )
    raise ValueError(f"Unknown relationship kind: {kind}")


def _yaml_load(raw: str) -> dict:
    if yaml is None:
        raise RuntimeError("PyYAML is not installed. Install 'pyyaml' to read YAML schema files.")
    payload: Any = yaml.safe_load(raw)
    if not isinstance(payload, dict):
        raise ValueError("Schema YAML must be a mapping.")
    return payload
