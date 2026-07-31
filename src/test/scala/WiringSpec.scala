import example.wiring.Wiring
import munit.FunSuite

object app {

  trait Connection { def url: String }
  final class PgConnection(val url: String) extends Connection

  final class Clock() { def now: Long = 0L }

  final class UserRepo(val conn: Connection) {
    def find(id: Int): String = s"user-$id@${conn.url}"
  }

  final class AuditLog(val clock: Clock)

  final class UserService(val repo: UserRepo, val audit: AuditLog) {
    def describe(id: Int): String = repo.find(id)
  }

  // A dependency that must come from the implicit scope, since `Wiring`
  // refuses to invent library types such as `String`.
  final class Tagged(val repo: UserRepo)(using val tag: String)

}

class WiringSpec extends FunSuite {

  import app.*

  test("recursively constructs the object graph") {
    given Connection = PgConnection("jdbc:pg")

    val service = Wiring.wire[UserService]
    assertEquals(service.describe(7), "user-7@jdbc:pg")
  }

  test("prefers a given over constructing a new instance") {
    given Connection     = PgConnection("jdbc:pg")
    given repo: UserRepo = new UserRepo(PgConnection("jdbc:from-given"))

    val service = Wiring.wire[UserService]
    assert(service.repo eq repo)
    assertEquals(service.describe(1), "user-1@jdbc:from-given")
  }

  test("resolves using-parameter lists through implicit search") {
    given Connection = PgConnection("jdbc:pg")
    given String     = "audit"

    val tagged = Wiring.wire[Tagged]
    assertEquals(tagged.tag, "audit")
  }

  test("wires a no-argument constructor") {
    val clock = Wiring.wire[Clock]
    assertEquals(clock.now, 0L)
  }

  // Does not compile, and the error names the path that needed it:
  //   Wiring.wire[UserService]
  //   => Cannot instantiate app.Connection: it is abstract. Provide a `given`.
  //      wiring path: app.UserService -> app.UserRepo -> app.Connection
}
