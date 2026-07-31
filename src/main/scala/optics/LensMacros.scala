package example.optics

import scala.quoted.*

/**
  * Implementation of [[LensBuilder.apply]].
  *
  * Techniques on show:
  *   - peeling the wrappers (`Inlined`, `Block`/`Closure`, `Typed`) the compiler puts around an
  *     inline lambda argument,
  *   - walking a `Select` chain back to the lambda parameter to recover the field path as a list of
  *     `Symbol`s,
  *   - synthesising a nested `copy(...)` call with `Apply`/`TypeApply` instead of quotes, because
  *     the field names are only known at expansion time,
  *   - mixing the two levels: quotes build the lambda skeleton, reflection builds the body,
  *     `asExprOf` stitches them together.
  */
private[optics] object LensMacros {

  def lensImpl[S: Type, A: Type](
      path: Expr[S => A]
  )(using Quotes): Expr[Lens[S, A]] = {
    import quotes.reflect.*

    def fail(msg: String): Nothing =
      report.errorAndAbort(
        msg + "\nA lens path must be a chain of case class field selections, e.g. `_.address.city`.",
        path
      )

    // `_.address.city` reaches the macro as
    //   Inlined(_, _, Block(List(DefDef(f, _, _, Some(body))), Closure(...)))
    // and the shape differs slightly depending on how the argument was written,
    // so strip everything that is not the lambda body itself.
    def lambdaBody(term: Term): Term = term match {
      case Inlined(_, _, inner)      => lambdaBody(inner)
      case Typed(inner, _)           => lambdaBody(inner)
      case Block(Nil, inner)         => lambdaBody(inner)
      case Block(List(d: DefDef), _) =>
        lambdaBody(d.rhs.getOrElse(fail("Empty lambda")))
      case other => other
    }

    // Walk `s.address.city` right-to-left down to the parameter `s`, keeping
    // the field symbols in source order.
    def fieldPath(term: Term, acc: List[Symbol]): List[Symbol] = term match {
      case Inlined(_, _, inner) => fieldPath(inner, acc)
      case Typed(inner, _)      => fieldPath(inner, acc)

      // A call such as `_.name.toUpperCase` arrives as `Apply(Select(...), _)`,
      // which would otherwise fall through to the catch-all and produce a much
      // vaguer message than it deserves.
      case Apply(Select(_, name), _) =>
        fail(s"`$name` is a method call, not a field selection")
      case TypeApply(Select(_, name), _) =>
        fail(s"`$name` is a method call, not a field selection")

      case sel @ Select(qual, _) =>
        val sym = sel.symbol
        if (!sym.isValDef && !sym.flags.is(Flags.ParamAccessor)) {
          fail(s"`${sym.name}` is a method call, not a field selection")
        }
        fieldPath(qual, sym :: acc)
      case Ident(_) => acc // reached the lambda parameter
      case other    =>
        fail(s"Unsupported expression in lens path: ${other.show}")
    }

    val fields = fieldPath(lambdaBody(path.asTerm), Nil)
    if (fields.isEmpty) {
      fail("The path selects the whole object, so there is nothing to focus on")
    }

    /**
      * `obj.f1.f2. ... .fn`
      */
    def focus(root: Term): Term =
      fields.foldLeft(root)((acc, field) => Select(acc, field))

    /**
      * `obj.copy(field = value)`, spelled out.
      *
      * `copy` has default arguments, and calling it with a subset of them from the reflection API
      * means synthesising `copy$default$N` calls. Passing *every* field - reading the untouched
      * ones straight back off `obj` - sidesteps that entirely, and is what Monocle does too.
      */
    def copyWith(obj: Term, field: Symbol, value: Term): Term = {
      val tpe = obj.tpe.widen
      val sym = tpe.typeSymbol

      if (!sym.flags.is(Flags.Case)) {
        fail(
          s"${sym.name} is not a case class, so `${field.name}` cannot be replaced (there is no `copy`)"
        )
      }

      val copySym = sym.declaredMethod("copy") match {
        case c :: Nil => c
        case Nil      => fail(s"${sym.name} has no `copy` method")
        case _        =>
          fail(s"${sym.name}.copy is overloaded, which is not supported")
      }

      val args = sym.caseFields.map { f =>
        if (f.name == field.name) value else Select(obj, f)
      }

      val fn = Select(obj, copySym)
      // A generic case class needs its type arguments re-applied: `copy[T](...)`.
      val applied =
        if (tpe.typeArgs.isEmpty) fn
        else TypeApply(fn, tpe.typeArgs.map(t => Inferred(t)))

      Apply(applied, args)
    }

    /**
      * Rebuild the structure from the inside out: for `_.a.b` this produces
      * `s.copy(a = s.a.copy(b = value))`.
      */
    def rebuild(root: Term, remaining: List[Symbol], value: Term): Term =
      remaining match {
        case Nil           => value
        case field :: rest =>
          copyWith(root, field, rebuild(Select(root, field), rest, value))
      }

    '{
      new Lens[S, A](
        (s: S) => ${ focus('s.asTerm).asExprOf[A] },
        (a: A) => (s: S) => ${ rebuild('s.asTerm, fields, 'a.asTerm).asExprOf[S] }
      )
    }
  }

}
