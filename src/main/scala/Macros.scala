package example

import scala.quoted.*

object Macros {

  inline def assertEqual[A](inline a: A, inline b: A): Unit =
    ${ assertEqualImpl('a, 'b) }

  private def assertEqualImpl[A: Type](a: Expr[A], b: Expr[A])(using
      Quotes
  ): Expr[Unit] = {
    import quotes.reflect.*

    (a.asTerm, b.asTerm) match {
      case (Literal(ca), Literal(cb)) if ca == cb =>
        '{ () } // They are equal, do nothing
      case _ =>
        report.error(s"Values are not equal: ${a.show} and ${b.show}")
        '{ () }
    }
  }

  def printType[T](using Type[T])(using Quotes): Unit = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    println(s"Type: ${tpe.show}")
  }

  inline def stringThings(inline s: String): Unit =
    ${ stringThingsImpl('s) }

  private def stringThingsImpl(s: Expr[String])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    s.value match {
      case Some(str) =>
        val upper  = str.toUpperCase
        val length = str.length
        println(s"Original: $str, Uppercase: $upper, Length: $length")
        '{ () }
      case None =>
        report.error("Expected a constant string")
        '{ () }
    }
  }

//A Quotes instance gives you access to compile-time reflection APIs:
  def myMacro(using Quotes) = {
    import quotes.reflect.*
    // Now you can inspect or transform the AST
  }

//Represents a typed expression of type T at compile time:
  // '{ 1 + 2 }   // Expr[Int]
  // '{ "hi" }    // Expr[String]

//You can splice expressions into quotes with $:

  def addOne(x: Expr[Int])(using Quotes): Expr[Int] =
    '{ $x + 1 }

  def example(using q: Quotes): Expr[Int] = {
    val x    = '{ 5 }
    val expr = '{ $x + 1 } // combines quoted code
    expr
  }

  inline def printExpr(inline x: Any): Unit =
    ${ printExprImpl('x) }

  private def printExprImpl(x: Expr[Any])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*
    val codeStr = x.show
    '{ println("Expression code: " + ${ Expr(codeStr) }) }
  }

  import scala.quoted.*

  inline def showType[T]: String = ${ showTypeImpl[T] }

  def showTypeImpl[T: Type](using Quotes): Expr[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]

    // Get detailed type info
    val name   = tpe.show
    val isCase = tpe.typeSymbol.flags.is(Flags.Case)
    val fields = tpe.typeSymbol.caseFields.map(_.name)

    Expr(s"Type: $name, isCase: $isCase, fields: $fields")
  }

  def optimizeImpl(expr: Expr[Int])(using Quotes): Expr[Int] = {
    expr match {
      // Match literal: x + 0 → x
      case '{ ($x: Int) + 0 } => x

      // Match literal: x * 1 → x
      case '{ ($x: Int) * 1 } => x

      // Match literal: x * 0 → 0
      case '{ ($x: Int) * 0 } => '{ 0 }

      // Match specific pattern: x - x → 0
      case '{ ($x: Int) - ($y: Int) } if x.matches(y) => '{ 0 }

      // No optimization
      case _ => expr
    }
  }

  inline def optimize(inline expr: Int): Int = ${ optimizeImpl('expr) }

  import scala.quoted.*

  inline def toMap[T](value: T): Map[String, Any] = ${ toMapImpl[T]('value) }

  def toMapImpl[T: Type](
      value: Expr[T]
  )(using Quotes): Expr[Map[String, Any]] = {
    import quotes.reflect.*

    val tpe    = TypeRepr.of[T]
    val fields = tpe.typeSymbol.caseFields

    val pairs: List[Expr[(String, Any)]] = fields.map { field =>
      val name       = field.name
      val fieldValue = Select(value.asTerm, field).asExpr
      '{ (${ Expr(name) }, $fieldValue) }
    }

    '{ Map(${ Varargs(pairs) }*) }
  }

  import scala.quoted.*

  inline def mustBePositive(inline n: Int): Int = ${ mustBePositiveImpl('n) }

  def mustBePositiveImpl(n: Expr[Int])(using Quotes): Expr[Int] = {
    import quotes.reflect.*

    n.value match {
      case Some(v) if v <= 0 =>
        report.errorAndAbort(s"Value must be positive, got: $v")
      case Some(v) =>
        report.info(s"Compile-time constant: $v")
        n
      case None =>
        report.warning("Cannot verify at compile time, will check at runtime")
        '{
          val x = $n
          if (x <= 0)
            throw new IllegalArgumentException(s"Must be positive: $x")
          x
        }
    }
  }

  import scala.quoted.*

  trait Show[T] {
    def show(t: T): String
  }

  inline def showAll[T](values: T*): List[String] = ${ showAllImpl[T]('values) }

  def showAllImpl[T: Type](
      values: Expr[Seq[T]]
  )(using Quotes): Expr[List[String]] = {
    // Summon Show[T] at compile time
    Expr.summon[Show[T]] match {
      case Some(showInstance) =>
        '{ $values.map(v => $showInstance.show(v)).toList }
      case None =>
        quotes.reflect.report.errorAndAbort(
          s"No Show instance found for ${Type.show[T]}"
        )
    }
  }

  import scala.quoted.*

  inline def tupleToList(t: Tuple): List[Any] = ${ tupleToListImpl('t) }

  def tupleToListImpl(t: Expr[Tuple])(using Quotes): Expr[List[Any]] = {
    import quotes.reflect.*

    def rec(expr: Expr[Tuple], tpe: TypeRepr): Expr[List[Any]] = {
      tpe.asType match {
        case '[EmptyTuple] =>
          '{ Nil }
        case '[h *: tail] =>
          val head     = '{ $expr.asInstanceOf[h *: tail].head }
          val tailExpr = '{ $expr.asInstanceOf[h *: tail].tail }
          val tailList = rec(tailExpr, TypeRepr.of[tail])
          '{ $head :: $tailList }
      }
    }

    rec(t, t.asTerm.tpe.widen)
  }

  import scala.quoted.*

  inline def enumName[E](value: E): String = ${ enumNameImpl[E]('value) }

  def enumNameImpl[E: Type](value: Expr[E])(using Quotes): Expr[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[E]
    val sym = tpe.typeSymbol

    if (!sym.flags.is(Flags.Enum)) {
      report.errorAndAbort(s"${Type.show[E]} is not an enum")
    }

    val cases = sym.children.map { child =>
      val childTpe = tpe.memberType(child)
      val pattern  = Ref(child).asExpr
      CaseDef(
        Typed(Wildcard(), TypeTree.of(using childTpe.asType)),
        None,
        Literal(StringConstant(child.name)).asExpr.asTerm
      )
    }

    Match(value.asTerm, cases).asExprOf[String]
  }

  import scala.compiletime.erasedValue

  inline def describe[T]: String = inline erasedValue[T] match {
    case _: Int     => "integer"
    case _: String  => "string"
    case _: List[t] => "list of " + describe[t]
    case _          => "unknown"
  }

  inline transparent def parse(s: String): Any = ${ parseImpl('s) }

  def parseImpl(s: Expr[String])(using Quotes): Expr[Any] = {
    s.value match {
      case Some("true") | Some("false")       => '{ $s.toBoolean }
      case Some(str) if str.forall(_.isDigit) => '{ $s.toInt }
      case _                                  => s
    }
  }

  import scala.deriving.Mirror
  import scala.quoted.*

  inline def derived[T](using m: Mirror.Of[T]): Show[T] =
    ${ derivedImpl[T]('m) }

  def derivedImpl[T: Type](
      m: Expr[Mirror.Of[T]]
  )(using Quotes): Expr[Show[T]] = {
    import quotes.reflect.*

    m match {
      case '{ $mp: Mirror.ProductOf[T] { type MirroredElemTypes = types } } =>
        val elemInstances = summonInstances[types]
        '{
          new Show[T] {
            def show(t: T): String = {
              val elems = t.asInstanceOf[Product].productIterator.toList
              val shown = elems.zip($elemInstances).map { (v, s) =>
                s.asInstanceOf[Show[Any]].show(v)
              }
              shown.mkString("(", ", ", ")")
            }
          }
        }

    }
  }

  private def summonInstances[T: Type](using Quotes): Expr[List[Show[Any]]] = {
    import quotes.reflect.*

    Type.of[T] match {
      case '[EmptyTuple] =>
        '{ Nil }
      case '[h *: tail] =>
        val headInstance  = Expr.summon[Show[Any]].get
        val tailInstances = summonInstances[tail]
        '{ $headInstance :: $tailInstances }
    }
  }

  import scala.compiletime.*
  import scala.compiletime.ops.int.*

// Type-level arithmetic
  type Add[A <: Int, B <: Int] = A + B
  type Ten                     = Add[3, 7] // Type is literally 10

// Compile-time string ops
  inline transparent def repeat(s: String, inline n: Int): String =
    inline if n <= 0 then ""
    else s + repeat(s, n - 1)

// Constvalue - extract singleton type as value
  inline transparent def sizeof[T] = inline erasedValue[T] match {
    case _: Byte  => 1
    case _: Short => 2
    case _: Int   => 4
    case _: Long  => 8
  }

}

import scala.compiletime.*
import scala.deriving.Mirror

trait SafePrinter[A] {
  def safeToString(value: A): String
}

object SafePrinter {

  given safeString: SafePrinter[String] =
    str => str

  given safeInt: SafePrinter[Long] =
    long => long.toString

  given safeArray[A](using A: SafePrinter[A]): SafePrinter[Array[A]] =
    array => array.view.map(A.safeToString).mkString("Array[", ", ", "]")

  // This provides SafePrinter for any case class!
  inline given automatic[A](using A: Mirror.Of[A]): SafePrinter[A] =
    inline A match {
      // Handling case class Mirror
      case p: Mirror.ProductOf[A] =>
        val name = valueOf[p.MirroredLabel]

        type ValuesOfFieldNames = Tuple.Map[p.MirroredElemLabels, ValueOf]
        val fieldNames =
          summonAll[ValuesOfFieldNames].productIterator
            .asInstanceOf[Iterator[ValueOf[String]]]
            .map(_.value)

        type SafePrinters = Tuple.Map[p.MirroredElemTypes, SafePrinter]
        lazy val safePrinters = summonAll[SafePrinters].productIterator
          .asInstanceOf[Iterator[SafePrinter[Any]]]

        CaseClassPrinter[A](name, fieldNames.zip(safePrinters))

      // Handling enum Mirror
      case s: Mirror.SumOf[A] =>
        type SafePrinters = Tuple.Map[s.MirroredElemTypes, SafePrinter]
        lazy val safePrinters = summonAll[SafePrinters].productIterator
          .asInstanceOf[Iterator[SafePrinter[A]]]

        EnumPrinter(s.ordinal, safePrinters)
    }

  // Newer version of Scala 3 complain about making anonymous classes
  // in inline code, probably because you are making tons of classes
  // which kills perf. And quite often you can just extract a few values
  // and pass them into one class.

  class CaseClassPrinter[A](
      name: String,
      makeFields: => Iterator[(String, SafePrinter[Any])]
  ) extends SafePrinter[A] {

    private lazy val fields = makeFields.toSeq

    override def safeToString(value: A): String =
      name + "(" + {
        fields
          .zip(value.asInstanceOf[Product].productIterator)
          .map { case ((fieldName, safePrinter), field) =>
            fieldName + " = " + safePrinter.safeToString(field)
          }
          .mkString(", ")
      } + ")"

  }

  class EnumPrinter[A](
      select: A => Int,
      makeChildren: => Iterator[SafePrinter[A]]
  ) extends SafePrinter[A] {

    private lazy val children = makeChildren.toArray

    override def safeToString(value: A): String =
      children(select(value)).safeToString(value)

  }

}
