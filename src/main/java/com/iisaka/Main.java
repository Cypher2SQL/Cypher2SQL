package com.iisaka;

import com.iisaka.cypher2sql.query.sql.BasicDialect;
import com.iisaka.cypher2sql.query.cypher.Query;
import com.iisaka.cypher2sql.schema.Mapping;
import com.iisaka.cypher2sql.schema.EdgeMapping;
import com.iisaka.cypher2sql.schema.NodeMapping;
import com.iisaka.cypher2sql.schema.SchemaDefinition;

public class Main {
    public static void main(final String[] args) {
        final SchemaDefinition schema = new SchemaDefinition()
                .addNode(new NodeMapping("Person", "people", "id"))
                .addNode(new NodeMapping("Movie", "movies", "id"))
                .addEdge(EdgeMapping.forJoinTable(
                        "ACTED_IN",
                        "Person",
                        "Movie",
                        "people_movies",
                        "person_id",
                        "movie_id"));

        final Query query = Query.parse("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie)");
        final Mapping mapping = new Mapping(schema);

        final String sql = mapping.toSql(query).render(new BasicDialect());
        System.out.println(sql);
    }
}
