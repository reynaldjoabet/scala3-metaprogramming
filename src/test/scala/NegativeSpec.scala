import example.config.ServerConfig
import example.json.JsonWriter
import example.literals.*
import example.optics.Lens
import example.sql.*
import example.staging.Arith
import example.wiring.Wiring
import munit.FunSuite

/** The macros in this project spend most of their effort on *rejecting* code.
  * These tests pin the rejections down: `compileErrors` compiles a snippet at
  * compile time and hands back the diagnostics as a string, so an error message
  * regressing is a test failure like any other.
  *
  * One caveat: the code under test lives inside string literals, so zinc cannot
  * see that this file depends on the macros. Editing a macro will not
  * invalidate these expansions - run them after a `clean` (or touch this file)
  * when you change an error message.
  */
class NegativeSpec extends FunSuite {

  test("lens paths must be field selections") {
    val errors = compileErrors("""Lens.of[Person](_.name.toUpperCase)""")
    assert(errors.contains("is a method call, not a field selection"), errors)
  }

  test("wiring reports the path to the missing dependency") {
    val errors = compileErrors("""Wiring.wire[wired.UserService]""")
    assert(errors.contains("it is abstract"), errors)
    assert(errors.contains("wiring path"), errors)
  }

  test("sql rejects a quoted parameter") {
    val errors =
      compileErrors("""val n = "x"; sql"select * from t where n = '$n'"""")
    assert(
      errors.contains("Do not wrap an interpolated value in quotes"),
      errors
    )
  }

  test("sql rejects a value with no encoder") {
    val errors = compileErrors("""val o = new Object; sql"select $o"""")
    assert(errors.contains("no given SqlEncoder"), errors)
  }

  test("sql rejects unbalanced parentheses") {
    val errors = compileErrors("""sql"select * from (t"""")
    assert(errors.contains("Unbalanced parentheses"), errors)
  }

  test("uuid literals must be canonical") {
    // `UUID.fromString` accepts this at runtime; the literal does not.
    val errors = compileErrors("""uuid"1-2-3-4-5"""")
    assert(errors.contains("Not a canonical UUID"), errors)
  }

  test("regex literals are parsed by the compiler") {
    val errors = compileErrors("""re"^[a-z"""")
    assert(errors.contains("Invalid regular expression"), errors)
  }

  test("config values are validated, not just typed") {
    val errors = compileErrors(
      """ServerConfig.parse("{\"host\":\"h\",\"port\":70000,\"tls\":true}")"""
    )
    assert(errors.contains("between 1 and 65535"), errors)
  }

  test("config keys must be known") {
    val errors = compileErrors(
      """ServerConfig.parse("{\"host\":\"h\",\"port\":80,\"tsl\":true}")"""
    )
    assert(errors.contains("unknown key"), errors)
  }

  test("staged expressions must be literal trees") {
    val errors = compileErrors(
      """val dynamic: Arith = Arith.Lit(1.0); Arith.compile(dynamic)"""
    )
    assert(errors.contains("needs a literal expression tree"), errors)
  }

  test("recursive types need an explicit instance") {
    val errors = compileErrors("""JsonWriter.derive[wired.Tree]""")
    assert(errors.contains("is recursive"), errors)
  }
}

object wired {
  trait Connection
  final class UserRepo(val conn: Connection)
  final class UserService(val repo: UserRepo)

  final case class Tree(children: List[Tree])
}
