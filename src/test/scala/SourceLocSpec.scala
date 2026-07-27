import example.sourceloc.SourceLoc
import example.sourceloc.Trace
import munit.FunSuite

class SourceLocSpec extends FunSuite {

  private def whereAmI(using loc: SourceLoc): SourceLoc = loc

  test("the given is resolved at each call site") {
    val first = whereAmI
    val second = whereAmI

    assertEquals(first.file, "SourceLocSpec.scala")
    assertEquals(second.line, first.line + 1)
    assert(first.column > 0)
    assert(first.enclosing.nonEmpty)
  }

  test("summoning directly works too") {
    val loc = summon[SourceLoc]
    assertEquals(loc.file, "SourceLocSpec.scala")
  }

  test("an expression's own source text is available to the macro") {
    val x = 20
    // `x + 1` is the text the user typed, recovered with Position.sourceCode.
    // The two calls are on consecutive lines, so the expected line follows.
    val here = summon[SourceLoc]
    val described = Trace.describe(x + 1)
    assertEquals(described, s"SourceLocSpec.scala:${here.line + 1}: x + 1 = 21")
  }
}
