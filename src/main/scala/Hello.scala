package example

import scala.quoted.Expr
import scala.quoted.Quotes
import scala.quoted.Type

object Hello extends Greeting {

  println(greeting)

  // Definition
  inline def debug(inline expr: Any): Any = ${ debugImpl('expr) }

  private def debugImpl(expr: Expr[Any])(using Quotes): Expr[Any] =
    '{
      println("debug: " + $expr)
      $expr
    }

}

trait Greeting {
  lazy val greeting: String = "hello"
}

object Named {

  inline def apply[A](inline a: A): (String, A) = ${ namedImpl('a) }

  private def namedImpl[A: Type](
      a: Expr[A]
  )(using Quotes): Expr[(String, A)] = {
    val codeAsString: Expr[String] = Expr(a.show)
    '{ ($codeAsString, $a) }
  }

}

// 1. DEFINITION (compile time of the macro library)
inline def greet(inline name: String): Unit = ${ greetImpl('name) }

def greetImpl(name: Expr[String])(using Quotes): Expr[Unit] = {
  // This code runs at COMPILE TIME of the user's code
  println("Macro is expanding!") // Prints during compilation!

  // This BUILDS an AST at compile time
  // The AST represents code that will run at RUNTIME
  '{ println("Hello, " + $name) }
}
