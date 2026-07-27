import example.staging.Arith
import example.staging.Arith.*
import munit.FunSuite

class ArithSpec extends FunSuite {

  test("the expression is folded during compilation") {
    assertEquals(Arith.optimize(Add(Lit(1.0), Lit(2.0))), Lit(3.0))
    assertEquals(Arith.optimize(Mul(Lit(1.0), Var("x"))), Var("x"))
    assertEquals(Arith.optimize(Mul(Lit(0.0), Var("x"))), Lit(0.0))
    assertEquals(Arith.optimize(Add(Var("x"), Lit(0.0))), Var("x"))
    assertEquals(Arith.optimize(Pow(Lit(2.0), 10)), Lit(1024.0))
  }

  test("compiled expressions evaluate correctly") {
    val f = Arith.compile(Add(Var("x"), Mul(Lit(2.0), Var("y"))))
    assertEquals(f(Map("x" -> 1.0, "y" -> 3.0)), 7.0)
  }

  test("powers are unrolled into multiplications") {
    val f = Arith.compile(Pow(Var("x"), 5))
    assertEquals(f(Map("x" -> 2.0)), 32.0)

    val reciprocal = Arith.compile(Pow(Var("x"), -2))
    assertEquals(reciprocal(Map("x" -> 4.0)), 0.0625)
  }

  test("a repeated variable is looked up once") {
    val f = Arith.compile(Add(Var("x"), Mul(Var("x"), Var("x"))))
    assertEquals(f(Map("x" -> 3.0)), 12.0)
  }

  test("no interpreter survives into the generated code") {
    val source = Arith.showCompiled(Add(Var("x"), Mul(Lit(2.0), Var("x"))))
    // The Arith tree existed only during compilation.
    assert(!source.contains("Arith"), source)
    // ... and what is left is arithmetic over one environment lookup.
    assert(source.contains("env.apply(\"x\")"), source)
  }

  // Does not compile - there would be no tree to inspect:
  //   val dynamic: Arith = Add(Var("x"), Lit(1.0))
  //   Arith.compile(dynamic)
  //     => Arith.compile needs a literal expression tree ...
}
