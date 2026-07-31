package example.literals

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

import scala.quoted.*

/**
  * Implementation of the `uuid` and `re` literals.
  *
  * The interesting technique here is error *positions*. `report.error` normally takes an `Expr` and
  * underlines the whole thing, which for a 60-character regex is close to useless.
  * `Position(sourceFile, start, end)` lets a macro build a span by hand, so the caret can land on
  * the single character inside the literal that the parser choked on:
  *
  * {{{
  * val bad = re"^[a-z"
  *              ^
  *              Unclosed character class
  * }}}
  */
private[literals] object LiteralMacros {

  def uuidImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using
      Quotes
  ): Expr[java.util.UUID] = {
    import quotes.reflect.*

    val part = singlePart(sc, args, "uuid")
    val text = part.valueOrAbort

    // `UUID.fromString` is famously lenient - it happily accepts "1-2-3-4-5".
    // A literal should be held to the canonical form.
    val canonical =
      text.length == 36 &&
        text.zipWithIndex.forall { case (c, i) =>
          if (i == 8 || i == 13 || i == 18 || i == 23) c == '-'
          else isHexDigit(c)
        }

    if (!canonical) {
      report.errorAndAbort(
        s"Not a canonical UUID: \"$text\" (expected 8-4-4-4-12 hex digits)",
        part
      )
    }

    val parsed =
      try java.util.UUID.fromString(text).nn
      catch {
        case e: IllegalArgumentException =>
          report.errorAndAbort(s"Invalid UUID \"$text\": ${e.getMessage}", part)
      }

    // Emit the decoded halves, not the text: no parsing survives to runtime.
    val high = Expr(parsed.getMostSignificantBits)
    val low  = Expr(parsed.getLeastSignificantBits)
    '{ new java.util.UUID($high, $low) }
  }

  def regexImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using
      Quotes
  ): Expr[Pattern] = {
    import quotes.reflect.*

    val part = singlePart(sc, args, "re")
    val text = part.valueOrAbort

    try {
      Pattern.compile(text)
      ()
    } catch {
      case e: PatternSyntaxException =>
        val description = Option(e.getDescription).getOrElse("invalid pattern")
        report.errorAndAbort(
          s"Invalid regular expression: $description",
          positionInside(part, e.getIndex)
        )
    }

    '{ Pattern.compile(${ Expr(text) }).nn }
  }

  /**
    * These literals take no interpolated values: an interpolated regex cannot be checked at compile
    * time, which is the entire point of the literal.
    */
  private def singlePart(
      sc: Expr[StringContext],
      args: Expr[Seq[Any]],
      name: String
  )(using Quotes): Expr[String] = {
    import quotes.reflect.*

    args match {
      case Varargs(Seq()) => ()
      case _              =>
        report.errorAndAbort(
          s"A $name literal cannot contain interpolated values - there would be nothing left to check at compile time.",
          args
        )
    }

    sc match {
      case '{ StringContext(${ Varargs(parts) }*) } if parts.sizeIs == 1 =>
        parts.head
      case _ =>
        report.errorAndAbort(s"Expected a single literal $name", sc)
    }
  }

  /**
    * Build a one-character span `offset` characters into a string literal.
    *
    * The literal's own position starts at the opening quote, so shift by one to land inside the
    * text, and clamp so that a parser reporting an index past the end still produces a valid span.
    */
  private def positionInside(part: Expr[String], offset: Int)(using
      Quotes
  ): quotes.reflect.Position = {
    import quotes.reflect.*

    val literal = part.asTerm.pos
    val start   = math.min(literal.start + 1 + math.max(offset, 0), literal.end)
    Position(literal.sourceFile, start, math.min(start + 1, literal.end))
  }

  private def isHexDigit(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

}
