package example.sql

/**
  * A parameterised SQL statement: the text never contains user data, only `?` placeholders, and the
  * values travel beside it.
  */
final case class SqlFragment(sql: String, params: List[SqlParam]) {

  def ++(that: SqlFragment): SqlFragment =
    SqlFragment(sql + that.sql, params ++ that.params)

}

/**
  * The closed set of things a driver knows how to bind.
  */
enum SqlParam {

  case Str(value: String)
  case I32(value: Int)
  case I64(value: Long)
  case F64(value: Double)
  case Bool(value: Boolean)
  case Absent // SQL NULL

}

trait SqlEncoder[A] {
  def encode(value: A): SqlParam
}

object SqlEncoder {

  given stringEncoder: SqlEncoder[String]   = v => SqlParam.Str(v)
  given intEncoder: SqlEncoder[Int]         = v => SqlParam.I32(v)
  given longEncoder: SqlEncoder[Long]       = v => SqlParam.I64(v)
  given doubleEncoder: SqlEncoder[Double]   = v => SqlParam.F64(v)
  given booleanEncoder: SqlEncoder[Boolean] = v => SqlParam.Bool(v)

  given optionEncoder[A](using inner: SqlEncoder[A]): SqlEncoder[Option[A]] = {
    case Some(a) => inner.encode(a)
    case None    => SqlParam.Absent
  }

}

/**
  * A checked SQL interpolator.
  *
  * {{{
  * sql"select name from users where id = $id and active = $active"
  * // SqlFragment("select name from users where id = ? and active = ?",
  * //             List(SqlParam.I32(7), SqlParam.Bool(true)))
  * }}}
  *
  * What the macro does at compile time:
  *   - refuses any interpolated value that has no `SqlEncoder`, so a value can never be pasted into
  *     the statement text,
  *   - rejects `... where name = '$name'` - quoting a bound parameter is the classic way to turn a
  *     safe query into a broken (or injectable) one,
  *   - rejects statements with unbalanced quotes or parentheses. Note this is checked per
  *     interpolation, so each fragment has to balance on its own: build a parenthesised group
  *     inside one `sql"..."`, not by gluing `sql"("` to `sql")"`,
  *   - splices nested `SqlFragment`s so queries compose,
  *   - folds the whole statement into a *single constant string* in the bytecode when no nested
  *     fragment is involved: there is no runtime concatenation at all.
  *
  * @see
  *   [[SqlMacros]] for the implementation.
  */
extension (inline sc: StringContext) {

  inline def sql(inline args: Any*): SqlFragment =
    ${ SqlMacros.sqlImpl('sc, 'args) }

}

/**
  * Statement text derived from a case class at compile time.
  *
  * Every method here returns a `String` *constant*: the column list is computed during compilation
  * and lands in the constant pool, so there is no startup cost and no chance of the statement
  * drifting from the model - add a field and the generated `insert` grows with it.
  */
object Table {

  /**
    * Field names, snake_cased: `case class User(userName: String)` gives `List("user_name")`.
    */
  inline def columns[T]: List[String] = ${ SqlMacros.columnsImpl[T] }

  /**
    * `insert into users (user_name, age) values (?, ?)`
    */
  inline def insertInto[T](inline table: String): String =
    ${ SqlMacros.insertImpl[T]('table) }

  /**
    * `select user_name, age from users`
    */
  inline def selectFrom[T](inline table: String): String =
    ${ SqlMacros.selectImpl[T]('table) }

}
