"""Read-only Cypher-to-SQL translator: parses Cypher queries, resolves them against a graph-to-relational
:class:`~cypher2sql.schema.SchemaDefinition`, and renders standard SQL.

Typical usage::

    from cypher2sql import Query, Mapping, SchemaDefinition, StandardGrammar

    schema = SchemaDefinition.from_yaml_string(yaml_text)
    query = Query.parse("MATCH (p:Person)-[:ACTED_IN]->(m:Movie) RETURN p, m")
    sql = Mapping(schema).to_sql(query).render(StandardGrammar())
"""

__docformat__ = "google"  # tells pdoc to parse Args/Returns/Raises sections in docstrings

from .cypher_query import Query as CypherQuery, Pattern, Node, Edge, ReturnItem
from .schema import SchemaDefinition, NodeMapping, EdgeMapping, RelationshipKind
from .read_query import ReadQuery, BoundPattern, BoundNode, BoundTraversal
from .sql_query import (
    Grammar,
    StandardGrammar,
    Renderable,
    SelectQuery,
    JoinClause,
    InsertQuery,
    UpdateQuery,
    DeleteQuery,
)
from .mapping import Mapping

# Backward-compatible alias for package consumers importing Query from cypher2sql.
Query = CypherQuery

__all__ = [
    "CypherQuery",
    "Query",
    "Pattern",
    "Node",
    "Edge",
    "ReturnItem",
    "SchemaDefinition",
    "NodeMapping",
    "EdgeMapping",
    "RelationshipKind",
    "ReadQuery",
    "BoundPattern",
    "BoundNode",
    "BoundTraversal",
    "Grammar",
    "StandardGrammar",
    "Renderable",
    "SelectQuery",
    "JoinClause",
    "InsertQuery",
    "UpdateQuery",
    "DeleteQuery",
    "Mapping",
]
