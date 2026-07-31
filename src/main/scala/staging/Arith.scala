package example.staging

/**
  * A tiny expression language, used to show *multi-stage programming*: an interpreter that is
  * specialised away at compile time.
  *
  * The naive way to evaluate `Add(Var("x"), Mul(Lit(2), Var("y")))` is to walk the tree at runtime -
  * one megamorphic `match` and one `Map` lookup per node, every single call. Staging removes the
  * interpreter entirely:
  *
  * {{{
  * Arith.compile(Add(Var("x"), Mul(Lit(2.0), Var("x"))))
  * // expands to:
  * (env: Map[String, Double]) => { val x = env("x"); x + 2.0 * x }
  * }}}
  *
  * The tree is *unlifted* into a real `Arith` value at compile time (`FromExpr`), simplified with
  * ordinary Scala code, and then emitted as straight-line arithmetic. This is the "interpreter to
  * compiler" step every staged query engine and regex compiler makes.
  *
  * @see
  *   [[ArithMacros]] for the implementation.
  */
enum Arith {

  case Lit(value: Double)
  case Var(name: String)
  case Add(left: Arith, right: Arith)
  case Mul(left: Arith, right: Arith)
  case Pow(base: Arith, exponent: Int)

}

object Arith {

  /**
    * Algebraic simplification. Ordinary Scala - it runs *inside* the macro, on a value that only
    * exists during compilation.
    */
  def fold(expr: Arith): Arith = expr match {
    case Add(l, r) =>
      (fold(l), fold(r)) match {
        case (Lit(0.0), x)    => x
        case (x, Lit(0.0))    => x
        case (Lit(a), Lit(b)) => Lit(a + b)
        case (x, y)           => Add(x, y)
      }

    case Mul(l, r) =>
      (fold(l), fold(r)) match {
        case (Lit(0.0), _)    => Lit(0.0)
        case (_, Lit(0.0))    => Lit(0.0)
        case (Lit(1.0), x)    => x
        case (x, Lit(1.0))    => x
        case (Lit(a), Lit(b)) => Lit(a * b)
        case (x, y)           => Mul(x, y)
      }

    case Pow(base, exponent) =>
      val folded = fold(base)
      if (exponent == 0) { Lit(1.0) }
      else if (exponent == 1) { folded }
      else {
        folded match {
          case Lit(v) => Lit(math.pow(v, exponent.toDouble))
          case other  => Pow(other, exponent)
        }
      }

    case leaf => leaf
  }

  def variables(expr: Arith): Set[String] = expr match {
    case Var(name) => Set(name)
    case Add(l, r) => variables(l) ++ variables(r)
    case Mul(l, r) => variables(l) ++ variables(r)
    case Pow(b, _) => variables(b)
    case Lit(_)    => Set.empty
  }

  def render(expr: Arith): String = expr match {
    case Lit(v)    => v.toString
    case Var(n)    => n
    case Add(l, r) => s"(${render(l)} + ${render(r)})"
    case Mul(l, r) => s"(${render(l)} * ${render(r)})"
    case Pow(b, n) => s"${render(b)}^$n"
  }

  /**
    * Simplify an expression at compile time and hand back the *value*.
    *
    * A round trip through both directions of the lifting machinery: `FromExpr` turns the syntax
    * tree into an `Arith`, `ToExpr` turns the simplified `Arith` back into a syntax tree.
    */
  inline def optimize(inline expr: Arith): Arith =
    ${ ArithMacros.optimizeImpl('expr) }

  /**
    * Compile an expression into a specialised function: no tree walk, no repeated environment
    * lookups, powers unrolled by binary exponentiation.
    */
  inline def compile(inline expr: Arith): Map[String, Double] => Double =
    ${ ArithMacros.compileImpl('expr) }

  /**
    * The generated source, as a compile-time constant - useful for tests and for seeing what
    * staging actually produced.
    */
  inline def showCompiled(inline expr: Arith): String =
    ${ ArithMacros.showCompiledImpl('expr) }

}
