import scala.compiletime.{constValue, summonInline}
import scala.quoted.*

import example.Hello.*

object Main {

  // Inline parameters and conditionals

  inline def choose[Cond <: Boolean, A, B](a: => A, b: => B) =
    inline if constValue[Cond] then a else b

  // Usage (branch erased at compile time):
  val x = choose[true, Int, String](42, "nope") // x: Int = 42

  val name: String | Null = "Scala3"

  val m = name.nn

  m.length()

  // Call site (where you use it)
  val y = 42
  debug(y + 1) // ← This is the CALL SITE

}

trait Eq[A] {
  def eqv(a: A, b: A): Boolean
}

object Eq {
  def apply[A](using e: Eq[A]): Eq[A] = e
}

opaque type Nullable[+T] = T | Null

object Nullable {

  extension [T](inline nullable: Nullable[T]) {

    inline transparent def fold[A](inline ifNull: => A)(inline fn: T => A): A =
      ${ foldImpl('nullable, 'ifNull, 'fn) }

    inline def isNull: Boolean = {
      given CanEqual[T | Null, Null] = CanEqual.derived
      (nullable: T | Null) == null
    }

    inline def nonNull: Boolean = {
      given CanEqual[T | Null, Null] = CanEqual.derived
      (nullable: T | Null) != null
    }

    inline def map[B](inline fn: T => B): Nullable[B] =
      nullable.fold(null: B | Null)(fn)

    inline def flatMap[B](inline fn: T => Nullable[B]): Nullable[B] =
      nullable.fold(null: B | Null)(fn)

    inline def toOption: Option[T] =
      nullable.fold(None: Option[T])(Some(_))

    inline def iterator: Iterator[T] =
      nullable.fold(Iterator.empty[T])(Iterator.single(_))

  }

  inline def apply[A](inline a: A | Null): Nullable[A] =
    a

  def fromOption[A](opt: Option[A]): Nullable[A] =
    opt match {
      case Some(a) => a
      case None    => null
    }

  given [A: Eq]: Eq[Nullable[A]] with {

    def eqv(na: Nullable[A], nb: Nullable[A]): Boolean = {
      val a: A | Null                = na
      val b: A | Null                = nb
      given CanEqual[A | Null, Null] = CanEqual.derived
      if (a == null) b == null
      else if (b == null) false
      else Eq[A].eqv(a.asInstanceOf[A], b.asInstanceOf[A])
    }

  }

  def empty[A]: Nullable[A] =
    null

  private def foldImpl[T: Type, A: Type](
      nullable: Expr[Nullable[T]],
      ifNull: Expr[A],
      fn: Expr[T => A]
  )(using Quotes): Expr[A] = {
    import quotes.reflect.*

    val nullableUnion: Expr[T | Null] = '{ $nullable.asInstanceOf[T | Null] }

    '{
      val n                          = $nullableUnion
      given CanEqual[T | Null, Null] = CanEqual.derived
      if (n == null) $ifNull
      else {
        val safe: T = n.asInstanceOf[T]
        ${ Expr.betaReduce('{ $fn(safe) }) }
      }
    }
  }

}

type Elem[X] = X match {
  case List[t]  => t
  case Array[t] => t
}

val hello: Elem[List[Int]] = 78

val arrInt: Elem[Array[Int]] = 90
