import unittest

from cypher2sql.sql_query import BasicSqlDialect, SqlInsert


class SqlInsertTest(unittest.TestCase):
    def test_placeholder_raises_in_read_only_mode(self) -> None:
        insert = SqlInsert.into("people").value("id", "1")
        self.assertFalse(insert.is_empty())
        with self.assertRaisesRegex(
            NotImplementedError,
            "Write queries are disabled in read-only mode. SqlInsert is reserved for future enhancement.",
        ):
            insert.render(BasicSqlDialect())


if __name__ == "__main__":
    unittest.main()
