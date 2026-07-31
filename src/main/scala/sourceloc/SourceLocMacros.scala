package example.sourceloc

import scala.quoted.*

/**
  * Implementation of [[SourceLoc]] and [[Trace]].
  *
  * Techniques on show:
  *   - `Position.ofMacroExpansion`: where the *call site* is, as opposed to where the macro is
  *     defined,
  *   - `Position.sourceCode`: the literal characters the user typed, which is strictly better than
  *     `Expr.show` (the latter prints the tree after typing, so it is full of inferred types and
  *     desugarings),
  *   - `Symbol.spliceOwner` and the owner chain, to name the enclosing method or class,
  *   - the `(using q: Quotes)(x: q.reflect.Symbol)` signature style, which is what you need
  *     whenever a helper takes a reflection type as a parameter: those types are path-dependent on
  *     the `Quotes` instance.
  */
private[sourceloc] object SourceLocMacros {

  def locImpl(using Quotes): Expr[SourceLoc] = {
    import quotes.reflect.*

    val position = Position.ofMacroExpansion

    // Everything reflective is computed *before* the quote and reduced to plain
    // values. Inside `'{ ... ${ ... } ... }` the splice runs under a fresh
    // `Quotes`, so calling `enclosingName(Symbol.spliceOwner)` in there mixes
    // two instances and fails with "Found: x$1.reflect.Symbol, Required:
    // contextual$4.reflect.Symbol". `Expr` values cross that boundary freely;
    // `Symbol`, `Term` and `TypeRepr` do not.
    val file      = Expr(position.sourceFile.name)
    val line      = Expr(position.startLine + 1) // reflection lines are 0-based
    val column    = Expr(position.startColumn + 1)
    val enclosing = Expr(enclosingName(Symbol.spliceOwner))

    '{ SourceLoc($file, $line, $column, $enclosing) }
  }

  def traceImpl[A: Type](value: Expr[A])(using Quotes): Expr[A] = {
    val label = Expr(labelOf(value))
    '{
      val result = $value
      println($label + result)
      result
    }
  }

  def describeImpl[A: Type](value: Expr[A])(using Quotes): Expr[String] = {
    val label = Expr(labelOf(value))
    '{ $label + $value }
  }

  private def labelOf[A](value: Expr[A])(using Quotes): String = {
    import quotes.reflect.*

    val position = Position.ofMacroExpansion
    val source   = value.asTerm.pos.sourceCode.getOrElse(value.show)
    s"${position.sourceFile.name}:${position.startLine + 1}: $source = "
  }

  /**
    * Walk up the owner chain to the nearest thing a human would recognise. The splice owner itself
    * is usually a synthetic `macro` symbol.
    */
  private def enclosingName(using
      q: Quotes
  )(symbol: q.reflect.Symbol): String = {
    import q.reflect.*

    def loop(current: Symbol): String = {
      if (current.isNoSymbol) { "<unknown>" }
      else if (current.isClassDef) { current.fullName }
      else if (current.isDefDef && !current.name.startsWith("$")) {
        current.owner.fullName + "." + current.name
      } else { loop(current.owner) }
    }

    loop(symbol)
  }

}
