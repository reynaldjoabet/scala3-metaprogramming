package example.sql

import scala.quoted.*

/** Implementation of the `sql` interpolator and of [[Table]].
  *
  * Techniques on show:
  *   - taking a `StringContext` apart with a quoted pattern (`'{
  *     StringContext(${Varargs(parts)}*) }`),
  *   - recovering the *static* type of an argument that was widened to `Any` by
  *     the varargs signature, then `Expr.summon`ing a type class for it,
  *   - compile-time lint over the literal parts, with the error attached to the
  *     offending argument's position,
  *   - constant folding: if nothing dynamic is spliced, the whole statement
  *     collapses to one `Expr(String)` literal.
  */
private[sql] object SqlMacros {

  def sqlImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using
      Quotes
  ): Expr[SqlFragment] = {
    import quotes.reflect.*

    val parts: List[String] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }*) } =>
        rawParts.map(_.valueOrAbort).toList
      case _ =>
        report.errorAndAbort(
          "The sql interpolator only works with literal string parts",
          sc
        )
    }

    val argExprs: List[Expr[Any]] = args match {
      case Varargs(as) => as.toList
      case _           =>
        report.errorAndAbort("Expected a varargs argument list", args)
    }

    // ---- compile-time lint ------------------------------------------------

    val staticText = parts.mkString(" ? ")

    if (staticText.count(_ == '\'') % 2 != 0) {
      report.errorAndAbort(
        "Unbalanced single quote in SQL statement: " + staticText,
        sc
      )
    }
    if (staticText.count(_ == '(') != staticText.count(_ == ')')) {
      report.errorAndAbort(
        "Unbalanced parentheses in SQL statement: " + staticText,
        sc
      )
    }

    argExprs.zipWithIndex.foreach { case (arg, i) =>
      if (parts(i).endsWith("'") && parts(i + 1).startsWith("'")) {
        report.errorAndAbort(
          "Do not wrap an interpolated value in quotes: it is bound as a query parameter, " +
            "so the quotes would end up in the value and the statement would be malformed. " +
            "Write `= $value`, not `= '$value'`.",
          arg
        )
      }
    }

    // ---- pieces -----------------------------------------------------------

    sealed trait Piece
    final case class Static(text: String) extends Piece
    final case class Param(expr: Expr[SqlParam]) extends Piece
    final case class Frag(expr: Expr[SqlFragment]) extends Piece

    /** The varargs signature is `Any*`, so every argument arrives typed as
      * `Any`. The *tree* still knows better, so peel the ascriptions off to get
      * the type the user actually wrote.
      */
    def staticType(expr: Expr[Any]): TypeRepr = {
      def strip(term: Term): TypeRepr = term match {
        case Inlined(_, _, inner) => strip(inner)
        case Typed(inner, _)      => strip(inner)
        case Block(Nil, inner)    => strip(inner)
        case other                => other.tpe.widen
      }
      strip(expr.asTerm)
    }

    val argPieces: List[Piece] = argExprs.map { arg =>
      staticType(arg).asType match {
        case '[SqlFragment] => Frag(arg.asExprOf[SqlFragment])
        case '[t]           =>
          Expr.summon[SqlEncoder[t]] match {
            case Some(encoder) =>
              Param('{ $encoder.encode(${ arg.asExprOf[t] }) })
            case None =>
              report.errorAndAbort(
                s"Cannot interpolate a value of type ${Type.show[t]} into a SQL statement: " +
                  s"no given SqlEncoder[${Type.show[t]}] is in scope.",
                arg
              )
          }
      }
    }

    val interleaved: List[Piece] =
      Static(parts.head) :: argPieces.zip(parts.tail).flatMap {
        case (p, part) =>
          List(p, Static(part))
      }

    // Merge neighbouring literals so the common case is a single constant.
    val pieces: List[Piece] = interleaved.foldRight(List.empty[Piece]) {
      case (Static(a), Static(b) :: rest) => Static(a + b) :: rest
      case (Static(""), rest)             => rest
      case (piece, rest)                  => piece :: rest
    }

    // ---- code generation --------------------------------------------------

    val hasFragment = pieces.exists {
      case Frag(_) => true
      case _       => false
    }

    val textOf: Piece => String = {
      case Static(t) => t
      case Param(_)  => "?"
      case Frag(_)   => ""
    }

    val sqlExpr: Expr[String] =
      if (!hasFragment) {
        // The entire statement is known now: emit one string constant.
        Expr(pieces.map(textOf).mkString)
      } else {
        val chunks: List[Expr[String]] = pieces.map {
          case Static(t) => Expr(t)
          case Param(_)  => Expr("?")
          case Frag(f)   => '{ $f.sql }
        }
        chunks.reduceLeft((acc, next) => '{ $acc + $next })
      }

    /** Group runs of single parameters into one `List(...)` allocation. */
    def paramChunks(
        remaining: List[Piece],
        pending: List[Expr[SqlParam]]
    ): List[Expr[List[SqlParam]]] = {
      def flushed(
          rest: List[Expr[List[SqlParam]]]
      ): List[Expr[List[SqlParam]]] =
        if (pending.isEmpty) rest else Expr.ofList(pending.reverse) :: rest

      remaining match {
        case Nil               => flushed(Nil)
        case Static(_) :: tail => paramChunks(tail, pending)
        case Param(e) :: tail  => paramChunks(tail, e :: pending)
        case Frag(f) :: tail   =>
          flushed('{ $f.params } :: paramChunks(tail, Nil))
      }
    }

    val paramsExpr: Expr[List[SqlParam]] = paramChunks(pieces, Nil) match {
      case Nil           => '{ List.empty[SqlParam] }
      case single :: Nil => single
      case many          => many.reduceLeft((acc, next) => '{ $acc ++ $next })
    }

    '{ SqlFragment($sqlExpr, $paramsExpr) }
  }

  // -------------------------------------------------------------------------
  // Statement text derived from the model, entirely at compile time.
  // -------------------------------------------------------------------------

  def columnsImpl[T: Type](using Quotes): Expr[List[String]] =
    Expr.ofList(columnNames[T].map(Expr(_)))

  def insertImpl[T: Type](table: Expr[String])(using Quotes): Expr[String] = {
    val cols = columnNames[T]
    val placeholders = cols.map(_ => "?").mkString(", ")
    Expr(
      s"insert into ${table.valueOrAbort} (${cols.mkString(", ")}) values ($placeholders)"
    )
  }

  def selectImpl[T: Type](table: Expr[String])(using Quotes): Expr[String] =
    Expr(s"select ${columnNames[T].mkString(", ")} from ${table.valueOrAbort}")

  private def columnNames[T: Type](using Quotes): List[String] = {
    import quotes.reflect.*

    val sym = TypeRepr.of[T].typeSymbol
    if (!sym.flags.is(Flags.Case)) {
      report.errorAndAbort(
        s"${Type.show[T]} is not a case class, so its columns cannot be derived"
      )
    }

    sym.caseFields.map(field => snakeCase(field.name))
  }

  private def snakeCase(name: String): String =
    name.flatMap { c =>
      if (c.isUpper) "_" + c.toLower else c.toString
    }
}
