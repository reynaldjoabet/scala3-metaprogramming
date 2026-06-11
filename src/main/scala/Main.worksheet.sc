val age: Int = 32

//val rockets:Short=434

Int.MaxValue
Int.MinValue

Byte.MaxValue
Byte.MinValue

val bigInt: BigInt = BigInt("123456789012345678901234567890")
Short.MaxValue
Short.MinValue

Long.MaxValue
Long.MinValue

Float.MaxValue
Float.MinValue

Double.MaxValue
Double.MinValue

/// calculate the memory size of an object
Char.MaxValue
Char.MinValue

//8bits ==1 byte
java.lang.Integer.SIZE

java.lang.Byte.SIZE

java.lang.Short.SIZE

java.lang.Long.SIZE

java.lang.Float.SIZE

java.lang.Double.SIZE

java.lang.Character.SIZE

val isEmployed: Boolean = true

class Student(name: String, age: Int)

val stu = new Student("Peter", 20)

val stu100 = new Student("Joan", 19)
class Employee(salary: Double, department: String)

//If you have an array of a million Ints, that's ~4MB.

(1 to 1_000_000).map(_ + 1)

import example.*
// 2. CALL SITE (user's code)
greet("World")

// 3. What happens during COMPILATION of user's code:
//    - greetImpl runs
//    - Prints "Macro is expanding!" to compiler output
//    - Returns Expr[Unit] containing AST for: println("Hello, " + "World")

// 4. What exists in compiled bytecode (RUNTIME):
println("Hello, " + "World")


inline def max(inline a: Int, inline b: Int): Int = if a > b then a else b     // dead branch is eliminated at compile time

max(3, 5)  //  compiles to just: 5


// inline parameters let you use arguments as compile-time constants:

inline val Limit = 1000       // compile-time constant, no allocation

inline def times(inline n: Int)(body: => Unit): Unit ={
  var i = 0
  while i < n do
    body
  i += 1
}
 times(3)(println("hi")) //unrolls to 3 explicit iterations


//  Inline match — pattern matching on types at compile time:


 inline def typeDescription[T]: String =
   inline compiletime.erasedValue[T] match {
     case _: Int    => "integer"
     case _: String => "string"
     case _: List[?]=> "list"
     case _         => "something else"
   }
 // typeDescription[Int] returns "integer" — computed at compile time, not runtime


// transparent inline — lets the return type be narrowed at call sites:


transparent inline def defaultOf(inline str: String): Any = ${ defaultOfImpl('str) }
// Caller sees the specific type (Int or String), not Any
val x = defaultOf("int")    // x: Int = 1
val y = defaultOf("string") // y: String = "a"

// These are the primitives for type-level arithmetic and type inspection:

import scala.compiletime.*

trait Show[A]{
    def show(value: A): String
}

given Show[Int] with {
    def show(value: Int): String = value.toString
}
// Extract a literal type value
val n: 42 = constValue[42]           // n = 42 at compile time

// Summon an instance at compile time, with a nice error if missing
val show = summonInline[Show[Int]]

// Iterate over a tuple type at compile time
inline def sumTuple[T <: Tuple](t: T): Int =
  inline erasedValue[T] match{
    case _: EmptyTuple => 0
    case _: (Int *: rest) =>
      t.asInstanceOf[Int *: rest].head + sumTuple(t.asInstanceOf[Int *: rest].tail)
  }