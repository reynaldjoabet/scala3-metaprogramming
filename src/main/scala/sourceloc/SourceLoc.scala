package example.sourceloc

/**
  * Where a call came from, resolved at compile time.
  *
  * Every logging library needs this: a log line wants the file and line of the *caller*, and the
  * only two ways to get it are a stack trace (expensive, and wrong under inlining) or a macro
  * (free). `SourceLoc.here` is a `given`, so a method can simply ask for one:
  *
  * {{{
  * def log(message: String)(using loc: SourceLoc): Unit =
  *   println(s"[$loc] $message")
  *
  * log("started") // [Service.scala:12:3 (com.example.Service.run)] started
  * }}}
  *
  * Each call site expands to a `SourceLoc(...)` built from string and int constants - there is
  * nothing to compute at runtime.
  *
  * @see
  *   [[SourceLocMacros]] for the implementation.
  */
final case class SourceLoc(
    file: String,
    line: Int,
    column: Int,
    enclosing: String
) {
  override def toString: String = s"$file:$line:$column ($enclosing)"
}

object SourceLoc {

  /**
    * Summoned afresh at every call site, because it is `inline`.
    */
  inline given here: SourceLoc = ${ SourceLocMacros.locImpl }
}

object Trace {

  /**
    * Print an expression's *source text* together with its value, then return the value - the trick
    * behind ScalaTest/munit style assertion messages.
    *
    * {{{
    * Trace.trace(user.age + 1) // Main.scala:8: user.age + 1 = 37
    * }}}
    */
  inline def trace[A](inline value: A): A = ${
    SourceLocMacros.traceImpl('value)
  }

  /**
    * As [[trace]], but returns the rendered line instead of printing it.
    */
  inline def describe[A](inline value: A): String =
    ${ SourceLocMacros.describeImpl('value) }

}
