import example.sql.*
import munit.FunSuite

final case class UserRow(userId: Int, userName: String, isActive: Boolean)

class SqlSpec extends FunSuite {

  test("parameters become placeholders, values travel separately") {
    val id     = 7
    val active = true
    val q      = sql"select name from users where id = $id and active = $active"

    assertEquals(q.sql, "select name from users where id = ? and active = ?")
    assertEquals(q.params, List(SqlParam.I32(7), SqlParam.Bool(true)))
  }

  test("a statement with no parameters is a single constant") {
    val q = sql"select 1"
    assertEquals(q.sql, "select 1")
    assertEquals(q.params, Nil)
  }

  test("Option encodes to NULL") {
    val nickname: Option[String] = None
    val q                        = sql"update users set nickname = $nickname"
    assertEquals(q.params, List(SqlParam.Absent))
  }

  test("fragments compose") {
    val minAge = 18
    val where  = sql"where age >= $minAge"
    val name   = "ada"
    val q      = sql"select * from users $where and name = $name order by id"

    assertEquals(
      q.sql,
      "select * from users where age >= ? and name = ? order by id"
    )
    assertEquals(q.params, List(SqlParam.I32(18), SqlParam.Str("ada")))
  }

  test("statement text is derived from the model") {
    assertEquals(
      Table.columns[UserRow],
      List("user_id", "user_name", "is_active")
    )
    assertEquals(
      Table.insertInto[UserRow]("users"),
      "insert into users (user_id, user_name, is_active) values (?, ?, ?)"
    )
    assertEquals(
      Table.selectFrom[UserRow]("users"),
      "select user_id, user_name, is_active from users"
    )
  }

  // None of these compile:
  //   sql"select * from users where name = '$name'"
  //     => Do not wrap an interpolated value in quotes ...
  //   sql"select * from users where id = ${new Object}"
  //     => no given SqlEncoder[Object] is in scope
  //   sql"select * from (users"
  //     => Unbalanced parentheses in SQL statement
}
