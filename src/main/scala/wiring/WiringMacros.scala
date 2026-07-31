package example.wiring

import scala.quoted.*

/**
  * Implementation of [[Wiring]].
  *
  * Techniques on show:
  *   - `Implicits.search`: running the compiler's own implicit resolution from inside a macro,
  *     against the *call site's* scope,
  *   - reading a constructor's signature by walking the `MethodType` chain (which also handles
  *     curried and `using` parameter lists),
  *   - `New` / `Select` / `TypeApply` / `Apply` to synthesise `new C(...)`,
  *   - carrying a resolution path so that failures blame the right dependency, with cycle and depth
  *     detection.
  */
private[wiring] object WiringMacros {

  private val MaxDepth = 32

  def wireImpl[A: Type](using Quotes): Expr[A] = build[A](debug = false)

  def wireDebugImpl[A: Type](using Quotes): Expr[A] = build[A](debug = true)

  private def build[A: Type](debug: Boolean)(using Quotes): Expr[A] = {
    import quotes.reflect.*

    def render(path: List[TypeRepr]): String =
      path.reverse.map(_.show).mkString(" -> ")

    def abort(msg: String, path: List[TypeRepr]): Nothing =
      report.errorAndAbort(
        s"$msg\n  wiring path: ${render(path)}"
      )

    /**
      * JDK and stdlib types are never auto-constructed: `new String()` is a legal but useless
      * answer to "where does this `String` come from?".
      */
    def isLibraryType(sym: Symbol): Boolean = {
      val name = sym.fullName
      name.startsWith("java.") || name.startsWith("javax.") ||
      name.startsWith("scala.") || sym.isNoSymbol
    }

    /**
      * Flatten a (possibly curried) constructor signature, remembering which lists are `using`
      * lists - those must come from implicit search only.
      */
    def paramLists(
        tpe: TypeRepr
    ): List[(Boolean, List[(String, TypeRepr)])] = tpe match {
      case mt: MethodType =>
        (mt.isImplicit, mt.paramNames.zip(mt.paramTypes)) :: paramLists(
          mt.resType
        )
      case _ => Nil
    }

    def instantiate(tpe: TypeRepr, path: List[TypeRepr]): Term = {
      val sym = tpe.typeSymbol

      if (path.length > MaxDepth) {
        abort(s"Dependency graph deeper than $MaxDepth levels", path)
      }
      if (path.tail.exists(_ =:= tpe)) {
        abort(s"Cyclic dependency on ${tpe.show}", path)
      }
      if (sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
        abort(
          s"Cannot instantiate ${tpe.show}: it is abstract. Provide a `given ${tpe.show}` in scope.",
          path
        )
      }
      if (isLibraryType(sym)) {
        abort(
          s"Refusing to construct the library type ${tpe.show}. Provide it as a `given`.",
          path
        )
      }

      val ctor = sym.primaryConstructor
      if (ctor.isNoSymbol) {
        abort(s"${tpe.show} has no primary constructor", path)
      }
      if (
        ctor.flags.is(Flags.Private) || ctor.flags.is(Flags.Protected) ||
        ctor.privateWithin.isDefined
      ) {
        abort(
          s"The primary constructor of ${tpe.show} is not accessible. Provide a `given ${tpe.show}` instead.",
          path
        )
      }

      // `Select(New(tpt), ctor).tpe` *is* the constructor's signature seen
      // through the prefix, so there is no need to reconstruct it by hand.
      val ctorRef = Select(New(Inferred(tpe)), ctor)
      val applied =
        if (tpe.typeArgs.isEmpty) ctorRef
        else TypeApply(ctorRef, tpe.typeArgs.map(t => Inferred(t)))

      val argss = paramLists(applied.tpe.widen).map { case (isUsing, params) =>
        params.map { case (name, paramTpe) =>
          resolve(paramTpe, isUsing, name, tpe, path)
        }
      }

      argss.foldLeft[Term](applied)((fn, args) => Apply(fn, args))
    }

    def resolve(
        paramTpe: TypeRepr,
        isUsing: Boolean,
        paramName: String,
        owner: TypeRepr,
        path: List[TypeRepr]
    ): Term = {
      // The compiler's own implicit search, run at the macro's call site.
      Implicits.search(paramTpe) match {
        case success: ImplicitSearchSuccess => success.tree
        case _ if isUsing                   =>
          abort(
            s"No given instance of ${paramTpe.show} for the `using` parameter `$paramName` of ${owner.show}",
            paramTpe :: path
          )
        case _ =>
          instantiate(paramTpe.dealias, paramTpe :: path)
      }
    }

    val tree = instantiate(TypeRepr.of[A].dealias, List(TypeRepr.of[A]))

    if (debug) {
      report.info(
        s"wire[${Type.show[A]}] = ${tree.show}",
        Position.ofMacroExpansion
      )
    }

    tree.asExprOf[A]
  }

}
