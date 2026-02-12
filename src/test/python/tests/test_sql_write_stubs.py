import unittest

from cypher2sql.sql_query import BasicDialect, DeleteQuery, InsertQuery, UpdateQuery


class SqlWriteStubTest(unittest.TestCase):
    def test_insert_placeholder_raises_in_read_only_mode(self) -> None:
        insert = InsertQuery.into("people").value("id", "1")
        self.assertFalse(insert.is_empty())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Write queries are disabled in read-only mode. InsertQuery is reserved for future enhancement.",
        ):
            insert.render(BasicDialect())

    def test_update_placeholder_raises_in_read_only_mode(self) -> None:
        update = UpdateQuery.table_name("people").set("name", "'Bob'").where("id = 1")
        self.assertTrue(update.has_assignments())
        self.assertTrue(update.has_where_clause())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Write queries are disabled in read-only mode. UpdateQuery is reserved for future enhancement.",
        ):
            update.render(BasicDialect())

    def test_delete_placeholder_raises_in_read_only_mode(self) -> None:
        delete = DeleteQuery.from_table("people").where("id = 1")
        self.assertTrue(delete.has_where_clause())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Write queries are disabled in read-only mode. DeleteQuery is reserved for future enhancement.",
        ):
            delete.render(BasicDialect())


if __name__ == "__main__":
    unittest.main()
