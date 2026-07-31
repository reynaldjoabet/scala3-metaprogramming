package example.staging

import scala.quoted.*

/**
  * Implementation of [[Arith]].
  *
  * Techniques on show:
  *   - [[FromExpr]] (*unlifting*): turning a syntax tree back into the value it denotes, so the
  *     macro can run ordinary Scala over it,
  *   - [[ToExpr]] (*lifting*): turning a value back into a syntax tree,
  *   - generating N bindings when N is only known at compile time, by recursing with nested quotes
  *     instead of building `ValDef`s by hand - `'{ val x = ...; ${ rest(using '{ x }) } }`,
  *   - unrolling a loop whose trip count is a compile-time constant (binary exponentiation).
  */
private[staging] object ArithMacros {

  // ---------------------------------------------------------------------------
  // Unlifting: Expr[Arith] => Arith
  // ---------------------------------------------------------------------------

  /**
    * Written as a plain recursive method rather than inside the `given` so the recursion is
    * explicit instead of going back through implicit search.
    */
  private def unlift(expr: Expr[Arith])(using Quotes): Option[Arith] =
    expr match {
      case '{ Arith.Lit(${ Expr(value) }) } => Some(Arith.Lit(value))
      case '{ Arith.Var(${ Expr(name) }) }  => Some(Arith.Var(name))
      case '{ Arith.Add($l, $r) }           =>
        for {
          a <- unlift(l)
          b <- unlift(r)
        } yield Arith.Add(a, b)
      case '{ Arith.Mul($l, $r) } =>
        for {
          a <- unlift(l)
          b <- unlift(r)
        } yield Arith.Mul(a, b)
      case '{ Arith.Pow($base, ${ Expr(exponent) }) } =>
        unlift(base).map(b => Arith.Pow(b, exponent))
      case _ => None
    }

  given arithFromExpr: FromExpr[Arith] with {
    def unapply(expr: Expr[Arith])(using Quotes): Option[Arith] = unlift(expr)
  }

  // ---------------------------------------------------------------------------
  // Lifting: Arith => Expr[Arith]
  // ---------------------------------------------------------------------------

  private def lift(value: Arith)(using Quotes): Expr[Arith] = value match {
    case Arith.Lit(v)    => '{ Arith.Lit(${ Expr(v) }) }
    case Arith.Var(n)    => '{ Arith.Var(${ Expr(n) }) }
    case Arith.Add(l, r) => '{ Arith.Add(${ lift(l) }, ${ lift(r) }) }
    case Arith.Mul(l, r) => '{ Arith.Mul(${ lift(l) }, ${ lift(r) }) }
    case Arith.Pow(b, n) => '{ Arith.Pow(${ lift(b) }, ${ Expr(n) }) }
  }

  given arithToExpr: ToExpr[Arith] with {
    def apply(value: Arith)(using Quotes): Expr[Arith] = lift(value)
  }

  // ---------------------------------------------------------------------------
  // The macros
  // ---------------------------------------------------------------------------

  def optimizeImpl(expr: Expr[Arith])(using Quotes): Expr[Arith] =
    Expr(Arith.fold(parse(expr)))

  def compileImpl(
      expr: Expr[Arith]
  )(using Quotes): Expr[Map[String, Double] => Double] =
    staged(Arith.fold(parse(expr)))

  def showCompiledImpl(expr: Expr[Arith])(using Quotes): Expr[String] =
    // `.show` renders a tree as source, which makes the staged output testable.
    Expr(staged(Arith.fold(parse(expr))).show)

  private def staged(
      optimized: Arith
  )(using Quotes): Expr[Map[String, Double] => Double] = {
    val names = Arith.variables(optimized).toList.sorted
    '{ (env: Map[String, Double]) =>
      ${ bindAll(names, 'env, Map.empty, optimized) }
    }
  }

  private def parse(expr: Expr[Arith])(using Quotes): Arith =
    unlift(expr).getOrElse {
      quotes.reflect.report.errorAndAbort(
        "Arith.compile needs a literal expression tree, so that it can be inspected at compile time. " +
          "`Add(Var(\"x\"), Lit(1.0))` works; a `val` holding an `Arith` does not.",
        expr
      )
    }

  /**
    * Emit `val x = env("x")` once per variable, then the body.
    *
    * The number of bindings is only known at expansion time, so the generator recurses, threading
    * the freshly bound references into the environment it passes down. Each
    * `'{ val binding = ...; ${ ... '{ binding } ... } }` adds one real `val` to the generated code.
    */
  private def bindAll(
      remaining: List[String],
      env: Expr[Map[String, Double]],
      bound: Map[String, Expr[Double]],
      body: Arith
  )(using Quotes): Expr[Double] = remaining match {
    case Nil          => generate(body, bound)
    case name :: rest =>
      '{
        val binding = $env(${ Expr(name) })
        ${ bindAll(rest, env, bound + (name -> 'binding), body) }
      }
  }

  private def generate(expr: Arith, bound: Map[String, Expr[Double]])(using
      Quotes
  ): Expr[Double] = expr match {
    case Arith.Lit(v) => Expr(v)

    case Arith.Var(name) =>
      bound.getOrElse(
        name,
        quotes.reflect.report.errorAndAbort(s"Unbound variable: $name")
      )

    case Arith.Add(l, r) =>
      '{ ${ generate(l, bound) } + ${ generate(r, bound) } }

    case Arith.Mul(l, r) =>
      '{ ${ generate(l, bound) } * ${ generate(r, bound) } }

    case Arith.Pow(base, exponent) =>
      // Bind the base first: `power` mentions it more than once, and without a
      // binding each mention would re-evaluate the whole sub-expression.
      // (The binding is named `b` and not `base`: inside the quote it would
      // shadow the pattern variable, and `val base = base` is a cycle.)
      '{
        val b = ${ generate(base, bound) }
        ${ power('b, exponent) }
      }
  }

  /**
    * `x^13` becomes 5 multiplications, decided at compile time.
    */
  private def power(base: Expr[Double], exponent: Int)(using
      Quotes
  ): Expr[Double] = {
    if (exponent < 0) { '{ 1.0 / ${ power(base, -exponent) } } }
    else if (exponent == 0) { '{ 1.0 } }
    else if (exponent == 1) { base }
    else if (exponent % 2 == 0) {
      '{
        val half = ${ power(base, exponent / 2) }
        half * half
      }
    } else { '{ $base * ${ power(base, exponent - 1) } } }
  }

}
