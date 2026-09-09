from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import textwrap
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - runtime dependency
    yaml = None


@dataclass(frozen=True)
class PropertyMapping:
    property: str
    column: str
    type: str | None = None
    nullable: bool = True
    quote: bool = False


@dataclass(frozen=True)
class NodeMapping:
    label: str
    table: str
    primary_key: str | None = None
    labels: list[str] | None = None
    inherits: list[str] | None = None
    catalog: str | None = None
    schema: str | None = None
    primary_keys: list[str] | None = None
    properties: dict[str, PropertyMapping] | None = None
    unique_keys: list[list[str]] | None = None

    def __post_init__(self) -> None:
        keys = self.primary_keys or ([self.primary_key] if self.primary_key else ["id"])
        object.__setattr__(self, "primary_keys", list(keys))
        object.__setattr__(self, "primary_key", keys[0] if len(keys) == 1 else None)
        object.__setattr__(self, "labels", list(self.labels or [self.label]))
        object.__setattr__(self, "inherits", list(self.inherits or []))
        object.__setattr__(self, "properties", dict(self.properties or {}))
        object.__setattr__(self, "unique_keys", [list(key) for key in (self.unique_keys or [])])

    @property
    def qualified_table(self) -> str:
        return ".".join(part for part in (self.catalog, self.schema, self.table) if part)

    def column_for_property(self, property_name: str) -> str:
        mapping = self.properties.get(property_name)
        return property_name if mapping is None else mapping.column

    def qualified_column(self, alias: str, property_name: str) -> str:
        return f"{alias}.{self.column_for_property(property_name)}"

    def qualified_primary_key(self, alias: str) -> str:
        if len(self.primary_keys) != 1:
            raise ValueError(f"Composite primary key is not scalar for label: {self.label}")
        return f"{alias}.{self.primary_keys[0]}"

    def join_on_columns(
        self, alias: str, columns: list[str], other_alias: str, other_columns: list[str]
    ) -> str:
        if len(columns) != len(other_columns):
            raise ValueError(f"Join key arity mismatch: {len(columns)} != {len(other_columns)}")
        return " AND ".join(
            f"{alias}.{column} = {other_alias}.{other_column}"
            for column, other_column in zip(columns, other_columns, strict=True)
        )


class RelationshipKind(Enum):
    JOIN_TABLE = "JOIN_TABLE"
    SELF_REFERENTIAL = "SELF_REFERENTIAL"
    ONE_TO_MANY = "ONE_TO_MANY"
    MANY_TO_ONE = "MANY_TO_ONE"


class Cardinality(Enum):
    ONE_TO_ONE = "ONE_TO_ONE"
    ONE_TO_MANY = "ONE_TO_MANY"
    MANY_TO_ONE = "MANY_TO_ONE"
    MANY_TO_MANY = "MANY_TO_MANY"


@dataclass(frozen=True)
class EdgeMapping:
    type: str
    from_label: str
    to_label: str
    relationship_kind: RelationshipKind
    cardinality: Cardinality | None = None
    unique: bool = False
    join_table: str | None = None
    from_join_key: str | None = None
    to_join_key: str | None = None
    from_join_keys: list[str] | None = None
    to_join_keys: list[str] | None = None
    from_key: str | None = None
    to_key: str | None = None
    from_keys: list[str] | None = None
    to_keys: list[str] | None = None
    parent_primary_key: str | None = None
    child_foreign_key: str | None = None
    parent_primary_keys: list[str] | None = None
    child_foreign_keys: list[str] | None = None
    properties: dict[str, PropertyMapping] | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "from_join_keys", list(self.from_join_keys or ([self.from_join_key] if self.from_join_key else [])))
        object.__setattr__(self, "to_join_keys", list(self.to_join_keys or ([self.to_join_key] if self.to_join_key else [])))
        object.__setattr__(self, "from_keys", list(self.from_keys or ([self.from_key] if self.from_key else [])))
        object.__setattr__(self, "to_keys", list(self.to_keys or ([self.to_key] if self.to_key else [])))
        object.__setattr__(
            self,
            "parent_primary_keys",
            list(self.parent_primary_keys or ([self.parent_primary_key] if self.parent_primary_key else [])),
        )
        object.__setattr__(
            self,
            "child_foreign_keys",
            list(self.child_foreign_keys or ([self.child_foreign_key] if self.child_foreign_key else [])),
        )
        object.__setattr__(self, "from_join_key", self.from_join_keys[0] if len(self.from_join_keys) == 1 else None)
        object.__setattr__(self, "to_join_key", self.to_join_keys[0] if len(self.to_join_keys) == 1 else None)
        object.__setattr__(self, "from_key", self.from_keys[0] if len(self.from_keys) == 1 else None)
        object.__setattr__(self, "to_key", self.to_keys[0] if len(self.to_keys) == 1 else None)
        object.__setattr__(self, "parent_primary_key", self.parent_primary_keys[0] if len(self.parent_primary_keys) == 1 else None)
        object.__setattr__(self, "child_foreign_key", self.child_foreign_keys[0] if len(self.child_foreign_keys) == 1 else None)
        object.__setattr__(self, "properties", dict(self.properties or {}))

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
            cardinality=Cardinality.MANY_TO_MANY,
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
            cardinality=Cardinality.MANY_TO_ONE,
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
            cardinality=Cardinality.ONE_TO_MANY,
            parent_primary_key=parent_primary_key,
            child_foreign_key=child_foreign_key,
        )


class SchemaDefinition:
    def __init__(self) -> None:
        self._nodes: dict[str, NodeMapping] = {}
        self._edges_by_key: dict[str, EdgeMapping] = {}
        self._edges_by_type: dict[str, list[EdgeMapping]] = {}

    def add_node(self, mapping: NodeMapping) -> "SchemaDefinition":
        self._nodes[mapping.label] = mapping
        return self

    def add_edge(self, mapping: EdgeMapping) -> "SchemaDefinition":
        key = self._edge_key(mapping.type, mapping.from_label, mapping.to_label)
        self._edges_by_key[key] = mapping
        self._edges_by_type.setdefault(mapping.type, []).append(mapping)
        return self

    def node_for_label(self, label: str) -> NodeMapping:
        if label not in self._nodes:
            raise ValueError(f"No node mapping for label: {label}")
        return self._nodes[label]

    def edge_for_type(self, type: str) -> EdgeMapping:
        mappings = self._edges_by_type.get(type, [])
        if not mappings:
            raise ValueError(f"No edge mapping for type: {type}")
        if len(mappings) > 1:
            pairs = ", ".join(f"{m.from_label}->{m.to_label}" for m in mappings)
            raise ValueError(f"Ambiguous edge mapping for type: {type}. Available pairs: {pairs}")
        return mappings[0]

    def edge_for_type_with_labels(self, type: str, from_label: str, to_label: str) -> EdgeMapping:
        key = self._edge_key(type, from_label, to_label)
        mapping = self._edges_by_key.get(key)
        if mapping is None:
            raise ValueError(f"No edge mapping for type/labels: {type} ({from_label}->{to_label})")
        return mapping

    def edge_for_type_undirected(self, type: str, left_label: str, right_label: str) -> EdgeMapping:
        forward = self._edges_by_key.get(self._edge_key(type, left_label, right_label))
        reverse = self._edges_by_key.get(self._edge_key(type, right_label, left_label))
        if forward is not None and reverse is not None and forward is not reverse:
            raise ValueError(f"Ambiguous undirected edge mapping for type/labels: {type} ({left_label}<->{right_label})")
        if forward is not None:
            return forward
        if reverse is not None:
            return reverse
        raise ValueError(f"No edge mapping for undirected type/labels: {type} ({left_label}<->{right_label})")

    @classmethod
    def from_json_string(cls, raw: str) -> "SchemaDefinition":
        return cls.from_dict(json.loads(raw))

    @classmethod
    def from_json_path(cls, path: str | Path) -> "SchemaDefinition":
        return cls.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))

    @classmethod
    def from_dict(cls, payload: dict) -> "SchemaDefinition":
        if not isinstance(payload, dict):
            raise ValueError("Schema payload must be a mapping.")
        schema = cls()
        for idx, node in enumerate(payload.get("nodes", [])):
            if not isinstance(node, dict):
                raise ValueError(f"Schema node entry at index {idx} must be a mapping.")
            schema.add_node(
                NodeMapping(
                    label=_require_string(node, "label", "node"),
                    table=_require_string(node, "table", "node"),
                    primary_key=None,
                    labels=_string_list(node.get("labels"), [_require_string(node, "label", "node")]),
                    inherits=_string_list(node.get("inherits"), []),
                    catalog=_optional_string(node.get("catalog")),
                    schema=_optional_string(node.get("schema")),
                    primary_keys=_string_list(_first_present(node, "primaryKeys", "primaryKey"), ["id"]),
                    properties=_property_mappings(node.get("properties")),
                    unique_keys=_nested_string_list(node.get("uniqueKeys")),
                )
            )
        for idx, edge in enumerate(payload.get("edges", [])):
            if not isinstance(edge, dict):
                raise ValueError(f"Schema edge entry at index {idx} must be a mapping.")
            kind = RelationshipKind(_require_string(edge, "kind", "edge"))
            schema.add_edge(_edge_mapping_from_payload(edge, kind))
        return schema

    @classmethod
    def from_yaml_string(cls, raw: str) -> "SchemaDefinition":
        return cls.from_dict(_yaml_load(raw))

    @classmethod
    def from_yaml_path(cls, path: str | Path) -> "SchemaDefinition":
        return cls.from_dict(_yaml_load(Path(path).read_text(encoding="utf-8")))

    @staticmethod
    def _edge_key(type: str, from_label: str, to_label: str) -> str:
        return f"{type}|{from_label}|{to_label}"


def _edge_mapping_from_payload(edge: dict, kind: RelationshipKind) -> EdgeMapping:
    cardinality = Cardinality(edge["cardinality"]) if isinstance(edge.get("cardinality"), str) else None
    unique = bool(edge.get("unique", False))
    properties = _property_mappings(edge.get("properties"))
    if kind is RelationshipKind.JOIN_TABLE:
        return EdgeMapping(
            type=_require_string(edge, "type", "edge"),
            from_label=_require_string(edge, "fromLabel", "edge"),
            to_label=_require_string(edge, "toLabel", "edge"),
            relationship_kind=RelationshipKind.JOIN_TABLE,
            cardinality=cardinality or Cardinality.MANY_TO_MANY,
            unique=unique,
            join_table=_require_string(edge, "joinTable", "edge"),
            from_join_keys=_string_list(_first_present(edge, "fromJoinKeys", "fromJoinKey"), []),
            to_join_keys=_string_list(_first_present(edge, "toJoinKeys", "toJoinKey"), []),
            properties=properties,
        )
    if kind is RelationshipKind.SELF_REFERENTIAL:
        return EdgeMapping(
            type=_require_string(edge, "type", "edge"),
            from_label=_require_string(edge, "label", "edge"),
            to_label=_require_string(edge, "label", "edge"),
            relationship_kind=RelationshipKind.SELF_REFERENTIAL,
            cardinality=cardinality or Cardinality.MANY_TO_ONE,
            unique=unique,
            from_keys=_string_list(_first_present(edge, "fromKeys", "fromKey"), []),
            to_keys=_string_list(_first_present(edge, "toKeys", "toKey"), []),
            properties=properties,
        )
    if kind is RelationshipKind.ONE_TO_MANY:
        return EdgeMapping(
            type=_require_string(edge, "type", "edge"),
            from_label=_require_string(edge, "parentLabel", "edge"),
            to_label=_require_string(edge, "childLabel", "edge"),
            relationship_kind=RelationshipKind.ONE_TO_MANY,
            cardinality=cardinality or Cardinality.ONE_TO_MANY,
            unique=unique,
            parent_primary_keys=_string_list(_first_present(edge, "parentPrimaryKeys", "parentPrimaryKey"), []),
            child_foreign_keys=_string_list(_first_present(edge, "childForeignKeys", "childForeignKey"), []),
            properties=properties,
        )
    if kind is RelationshipKind.MANY_TO_ONE:
        return EdgeMapping(
            type=_require_string(edge, "type", "edge"),
            from_label=_require_string(edge, "fromLabel", "edge"),
            to_label=_require_string(edge, "toLabel", "edge"),
            relationship_kind=RelationshipKind.MANY_TO_ONE,
            cardinality=cardinality or Cardinality.MANY_TO_ONE,
            unique=unique,
            parent_primary_keys=_string_list(_first_present(edge, "toPrimaryKeys", "toPrimaryKey"), []),
            child_foreign_keys=_string_list(_first_present(edge, "fromForeignKeys", "fromForeignKey"), []),
            properties=properties,
        )
    raise ValueError(f"Unknown relationship kind: {kind}")


def _yaml_load(raw: str) -> dict:
    if yaml is None:
        payload = _simple_schema_yaml_load(raw)
    else:
        payload = yaml.safe_load(raw)
    if not isinstance(payload, dict):
        raise ValueError("Schema YAML must be a mapping.")
    return payload


def _require_string(payload: dict[str, Any], key: str, context: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Schema {context} missing required string field: {key}")
    return value


def _optional_string(value: Any) -> str | None:
    return value if isinstance(value, str) and value.strip() else None


def _first_present(payload: dict[str, Any], primary_key: str, fallback_key: str) -> Any:
    return payload[primary_key] if primary_key in payload else payload.get(fallback_key)


def _string_list(raw: Any, default: list[str]) -> list[str]:
    if raw is None:
        return list(default)
    if isinstance(raw, str):
        return [raw]
    if isinstance(raw, list) and all(isinstance(item, str) and item.strip() for item in raw):
        return list(raw)
    raise ValueError("Schema value must be a string or list of strings.")


def _nested_string_list(raw: Any) -> list[list[str]]:
    if raw is None:
        return []
    if not isinstance(raw, list):
        raise ValueError("Schema uniqueKeys must be a list.")
    return [_string_list(item, []) for item in raw]


def _property_mappings(raw: Any) -> dict[str, PropertyMapping]:
    if raw is None:
        return {}
    if not isinstance(raw, dict):
        raise ValueError("Schema properties must be a mapping.")
    properties: dict[str, PropertyMapping] = {}
    for prop, value in raw.items():
        if not isinstance(prop, str) or not prop.strip():
            raise ValueError("Schema property names must be non-blank strings.")
        if isinstance(value, str):
            properties[prop] = PropertyMapping(prop, value)
            continue
        if isinstance(value, dict):
            properties[prop] = PropertyMapping(
                property=prop,
                column=_require_string(value, "column", "property"),
                type=_optional_string(value.get("type")),
                nullable=bool(value.get("nullable", True)),
                quote=bool(value.get("quote", False)),
            )
            continue
        raise ValueError("Schema property mapping must be a string or mapping.")
    return properties


def _simple_schema_yaml_load(raw: str) -> dict[str, Any]:
    raw = textwrap.dedent(raw)
    payload: dict[str, Any] = {}
    current_section: str | None = None
    current_item: dict[str, str] | None = None

    for line in raw.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if stripped.startswith("- ") and current_section is None:
            raise ValueError("Schema YAML must be a mapping.")

        indent = len(line) - len(line.lstrip(" "))
        if indent == 0:
            if not stripped.endswith(":"):
                raise ValueError("Schema YAML must be a mapping.")
            section = stripped[:-1].strip()
            payload[section] = []
            current_section = section
            current_item = None
            continue

        if indent == 2 and stripped.startswith("- "):
            if current_section is None:
                raise ValueError("Schema YAML must be a mapping.")
            current_item = {}
            payload[current_section].append(current_item)
            inline = stripped[2:].strip()
            if inline:
                key, value = _parse_yaml_key_value(inline)
                current_item[key] = value
            continue

        if indent >= 4:
            if current_item is None:
                raise ValueError("Schema YAML must be a mapping.")
            key, value = _parse_yaml_key_value(stripped)
            current_item[key] = value
            continue

        raise ValueError("Schema YAML must be a mapping.")

    return payload


def _parse_yaml_key_value(text: str) -> tuple[str, str]:
    if ":" not in text:
        raise ValueError("Schema YAML must be a mapping.")
    key, value = text.split(":", 1)
    parsed_key = key.strip()
    parsed_value = value.strip()
    if parsed_value.startswith(("'", '"')) and parsed_value.endswith(("'", '"')) and len(parsed_value) >= 2:
        parsed_value = parsed_value[1:-1]
    return parsed_key, parsed_value
