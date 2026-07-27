# Scala3-Macros-Metaprogramming
The Core Idea: "Code that writes code"
Scala 3 gives you static metaprogramming built on:
- Inline: evaluate code at compile-time when arguments are known.
- Quotes & Splices: build and transform syntax trees (macros).
- Type-level ops: summon givens, compute with types, match types.
- Derivation: auto-create typeclass instances via Mirror

## Inline basics (zero macros, already powerful)

You can't define and use a macro in the same file

macros let you generate or transform code at compile time.

- Quotes — the context needed for quoting and splicing code
- Expr — a representation of typed expressions
- Type — a representation of types
- ToExpr / FromExpr — typeclass interfaces for converting between runtime values and compile-time expressions

When you import quotes.reflect.*, you get access to compiler internals, like:
Term, TypeTree, Symbol, DefDef, ValDef, etc.
Useful for inspecting and transforming abstract syntax trees (ASTs).

validating SQL queries, JSON schemas, or file paths at compile time

### Type-Safe Database Queries (DSLs like Doobie or Slick)
Macros can analyze and generate SQL queries based on Scala code, ensuring type correctness.
For example, a macro could:
Parse a Scala case class
Generate an SQL INSERT or SELECT statement automatically
Ensure field names and types match
This is how libraries like `Slick` and `Quill` achieve compile-time safety for queries.

### Compile-Time Code Generation
Macros can generate boilerplate code automatically — such as:
JSON (de)serialization
Equality and hashing methods
Typeclass instances
Example — Auto-Derivation of Typeclasses

### Inlining and Performance Optimization
Macros can inline complex logic so that unnecessary computations are eliminated at compile time

### Safer DSLs (Domain-Specific Languages)
Macros let you build fluent, type-safe DSLs that look like natural Scala code but have compile-time checking.
Examples:
HTML builders (scalatags, lihaoyi)
SQL/GraphQL DSLs (Caliban, Sangria)
Testing frameworks (like ScalaTest’s assert macros)

## Boxing
If you use a primitive in a context that requires an object (like putting an Int into a List or a Map), the JVM "boxes" it into a wrapper object (e.g., java.lang.Integer).

- Unboxed (Primitive): Int = 4 bytes.
- Boxed (Object): Integer object = 16 bytes (12-byte header + 4-byte value)

Int usually represents a single word, while long represents double word

A word - is a number of bits for majority of the cpu registers

Boxed(reference) types are primitive data types wrapped in an object. Values of such types are accessed through a pointer

Why a performance penalty for using box types?
- Stack vs Heap: primitive types are allocated on the stack while boxed types live in the heap. Stack memory is way faster than heap to access
- Data Size: Boxing overhead is significantWhile regular int takes only 4 bytes of space (32-bits),Integer type takes 16 bytes(128-bits)
- Boxed Objects: The collection (like a List) doesn't hold the numbers; it holds pointers to objects scattered elsewhere on the heap. The CPU has to "follow the pointer" to find the actual value. This often results in a cache miss, forcing the CPU to wait for the much slower RAM

[primitive-vs-boxed-performance](https://alammori.com/benchmarks/primitive-vs-boxed-performance)

When CPU fetches data from memory it never fetches just one value. Instead it fetches what is called a cache line. The size of cache line is usually 64 Bytes, at least for Intel and AMD

What that means, is that CPU fetches an entire block from RAM to its cache and doesn’t have to do expensive round trips to RAM as long as it has required data in the cache.

In this case it played a significant role, because our variables were co-allocated in RAM, so each fetch cycle retrieved anywhere from 1 to 16 variables.

array of Integers is an array of pointers. Although the pointers were fetched from the RAM, on each access CPU has to do one more trip to the RAM to fetch actual data

Because your Int primitives are packed together, that one 64-byte fetch actually brings in 16 integers (64÷4=16).

When you declare a variable for an object (e.g., User person = new User();), the variable itself (the address or pointer) is stored on the Stack.

- The stack is fast and handles local variables within a function's scope.
- Once the function finishes, the stack frame is cleared, and that reference is gone.

The object's contents (its fields, properties, and values) are stored on the Heap.
- The heap is a large pool of memory used for dynamic allocation.
- When you use the new keyword, you are telling the system to find a spot on the heap big enough to hold that object's data

Stack size is usually fixed and much smaller than the heap (often 1MB to 8MB by default, depending on the OS).

Every time a thread calls a function, it pushes a new "frame" onto its stack. This frame contains local variables and the return address.

### Call Site
Where the macro is invoked in user code.

```scala
// Definition
inline def debug(inline expr: Any): Unit = ${ debugImpl('expr) }

// Call site (where you use it)
val x = 42
debug(x + 1)  // ← This is the CALL SITE
```

### Definition Site
Where the macro is defined

```scala
// Definition site ↓
inline def debug(inline expr: Any): Unit = ${ debugImpl('expr) }

def debugImpl(expr: Expr[Any])(using Quotes): Expr[Unit] = {
  // Macro implementation lives here
  '{ println(${Expr(expr.show)} + " = " + $expr) }
}

```
### Expansion Site
Where the macro's generated code is inserted (usually the same as call site).

```scala
// Before expansion (call site)
debug(x + 1)

// After expansion (expansion site - same location)
println("x + 1" + " = " + (x + 1))
```
### Splice Site
Where `${ ... }` appears inside a macro—triggers compile-time execution.

```scala
inline def show[T]: String = ${ showImpl[T] }  // ← Splice site
//                           ^^^^^^^^^^^
```
### Quote Site
Where `'{ ... }` appears—creates code that will exist at runtime.
```scala
def showImpl[T: Type](using Quotes): Expr[String] = {
  val typeName = Type.show[T]
  '{ "Type: " + ${Expr(typeName)} }  // ← Quote site
//^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
}
```

### The Splice ( ) is the "Hole" in the Compiler

`inline def example: Int = ${ /* HOLE - escape to compile-time */ }`

```sh
┌─────────────────────────────────────────────────────────┐
│  RUNTIME WORLD (normal Scala code)                      │
│                                                         │
│    val x = 1 + 2                                        │
│    val y = ${ ══════════════════════════╗               │
│              ║  COMPILE-TIME WORLD      ║               │
│              ║  (macro execution)       ║               │
│              ║                          ║               │
│              ║  // Can inspect types    ║               │
│              ║  // Generate code        ║               │
│              ║  // Access compiler      ║               │
│              ╚══════════════════════════╝ }             │
│    val z = y + 1                                        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

Quotes Fill the Hole Back
And `'{ } `is how you return from the hole back to runtime:

```scala
def macroImpl(using Quotes): Expr[Int] = {
  // Inside the hole (compile-time)
  val computed = 1 + 2 + 3  // Runs at compile time
  
  '{ 100 }  // "Fill the hole" with this runtime code
  // ↑ Returns an Expr that becomes the value at the call site
}

inline def example: Int = ${ macroImpl }
// After expansion: val x: Int = 100
```

Quotes Build Code, They Don't Run It
`'{ ... }` doesn't execute the code—it constructs an AST (Abstract Syntax Tree) representing that code.

`${ ... }` (splice)	Executes Scala code	Compile time
`'{ ... }` (quote)	Builds an AST representing code.The quote itself runs at compile time, but the code inside runs at runtime

```sh
COMPILE TIME                          RUNTIME
─────────────────────────────────────────────────────────
                                      
${ greetImpl('name) }                 
    │                                 
    ▼                                 
greetImpl executes                    
    │                                 
    ▼                                 
'{ println(...) }                     
    │                                 
    │ (builds AST)                    
    ▼                                 
Expr[Unit] returned                   
    │                                 
    │ (inserted into program)         
    ▼                                 
─────────────────────────────────────────────────────────
                                      println("Hello, World")
                                          │
                                          ▼
                                      "Hello, World" printed
```                                     
When you write `'{ println("Hello") }`, you are not executing `println`.
- Instead, you are creating a Data Structure (an `Expr[Unit]`) that represents the idea of printing `"Hello."`
- In Scala 3, a `Quote` is essentially a serialized AST (`Abstract Syntax Tree`).

Inlining: The compiler takes that tree and pastes it directly into your source code where the `Splice` was


```scala
inline def query[T]: EntityQuery[T] = ${ QueryMacro[T] }
inline def select[T]: Query[T] = ${ QueryMacro[T] }

def max[A](a: A): A = NonQuotedException()
def min[A](a: A): A = NonQuotedException()
def count[A](a: A): A = NonQuotedException()
def avg[A](a: A)(implicit n: Numeric[A]): BigDecimal = NonQuotedException()
def sum[A](a: A)(implicit n: Numeric[A]): A = NonQuotedException()

def avg[A](a: Option[A])(implicit n: Numeric[A]): Option[BigDecimal] = NonQuotedException()
def sum[A](a: Option[A])(implicit n: Numeric[A]): Option[A] = NonQuotedException()

extension [T](o: Option[T]) {
  def filterIfDefined(f: T => Boolean): Boolean = NonQuotedException()
}

object extras extends DateOps {
  extension [T](a: T) {
    def getOrNull: T =
      throw new IllegalArgumentException(
        "Cannot use getOrNull outside of database queries since only database value-types (e.g. Int, Double, etc...) can be null."
      )

def ===(b: T): Boolean =
  (a, b) match {
    case (a: Option[_], b: Option[_]) => 
      a.exists(av => b.exists(bv => av == bv))
    case (a: Option[_], b)            => 
      a.exists(av => av == b)
    case (a, b: Option[_])            => 
      b.exists(bv => bv == a)
    case (a, b)                       => a == b
  }

def =!=(b: T): Boolean =
  (a, b) match {
    case (a: Option[_], b: Option[_]) => 
      a.exists(av => b.exists(bv => av != bv))
    case (a: Option[_], b)            => 
      a.exists(av => av != b)
    case (a, b: Option[_])            => 
      b.exists(bv => bv != a)
    case (a, b)                       => a != b
  }
  }
}

inline def static[T](inline value: T): T = ${ StaticSpliceMacro('value) }

inline def insertMeta[T](inline exclude: (T => Any)*): InsertMeta[T] = ${ InsertMetaMacro[T]('exclude) }

inline def updateMeta[T](inline exclude: (T => Any)*): UpdateMeta[T] = ${ UpdateMetaMacro[T]('exclude) }

inline def lazyLift[T](inline vv: T): T = ${ LiftMacro.applyLazy[T, Nothing]('vv) }

inline def quote[T](inline bodyExpr: Quoted[T]): Quoted[T] = ${ QuoteMacro[T]('bodyExpr) }

inline def quote[T](inline bodyExpr: T): Quoted[T] = ${ QuoteMacro[T]('bodyExpr) }

inline implicit def unquote[T](inline quoted: Quoted[T]): T = ${ UnquoteMacro[T]('quoted) }

inline implicit def autoQuote[T](inline body: T): Quoted[T] = ${ QuoteMacro[T]('body) }

```

The call site is where the inline method is used.. `quote` is inline, very important

`Quotes, Expr[T], and Type[T]` — The Macro API Foundation
Every macro implementation receives a Quotes context parameter

`Quotes` — Gives access to the compiler's reflection API via quotes.reflect._
`Expr[T]` — A typed representation of a Scala expression at compile time. It's code-as-data.
`Type[T]` — A compile-time representation of a type. Passed via using `Type[T]`.
`Expr[T]` is not a value — it's a description of code that will eventually produce a value of type T at runtime.

Transparent Inline
This is a Scala 3 feature that allows a macro to change its return type based on the input.

`Expr[T]`: Represents a typed abstract syntax tree (AST) of code that will evaluate to a value of type T. You build these using quotes ('{ ... }) and evaluate them using splices (${ ... }).

`Type[T]`: Represents a Scala type. Used to pass type information into macros.

`quotes.reflect.*`: The low-level API. When you need to inspect case class fields, check class flags, or manually stitch together ASTs (Term, Tree, Symbol), you drop down into the reflection API.


[sanely-automatic-derivation](https://kubuszok.com/2025/sanely-automatic-derivation/)

It creates a "phantom" value of any type T that only exists at compile time and is completely erased before runtime.

```scala
import scala.compiletime.erasedValue

// Returns a phantom value of type T — never actually instantiated
erasedValue[T]

```


```scala
inline def apply[This >: this.type <: Tuple](n: Int): Elem[This, n.type] =
  runtime.Tuples.apply(this, n).asInstanceOf[Elem[This, n.type]]
```
`n.type` is the singleton type of the argument `n`


Because the method is `inline`, the compiler expands it at the call site. That matters because the return type mentions things like `this.type` and `n.type` (singleton types), and inlining helps the compiler keep those precise.

`Match types: type-level functions over tuples`

In object Tuple, you’ve got:
`Head`, `Tail`, `Last`, `Init`,`Concat` (`++`),`Elem`,`Size`,`Map`, `FlatMap`, `Filter`, `Zip`,`Fold` and derived helpers like `Union`
These are “functions at the type level”. Example:

```scala
type Elem[X <: Tuple, N <: Int] = X match {
  case x *: xs =>
    N match {
      case 0 => x
      case S[n1] => Elem[xs, n1]
    }
}

type First = Elem[(String, Int, Boolean), 0]  // String

type Head[X <: Tuple] = X match {
  case x *: _ => x
}
```

The tuple type `(A, B, C)` is encoded as: `A *: (B *: (C *: EmptyTuple))`

## Newtypes and Refined Types
newtypes and refined types both improve type safety, but they serve different mathematical and domain-modeling purposes:
## Newtypes (Identity / Distinction)
A `newtype` creates a completely distinct, incompatible type from an underlying base type to prevent accidental mixing of logically different concepts. It has zero runtime overhead.

Purpose: To differentiate domain concepts that happen to share the same underlying representation.
Relationship: A `UserId` is not a `String`. An `OrderId` is not a `String`. You cannot add them or mix them up.
Scala 3 Implementation: Achieved natively using `opaque type`
```scala
opaque type UserId = String
object UserId:
  def apply(s: String): UserId = s

opaque type OrderId = String
object OrderId:
  def apply(s: String): OrderId = s
  
val u: UserId = UserId("123")
// val o: OrderId = u // Compile error!
```
## Refined Types (Validation / Constraints)
A `refined` type restricts the allowed values of an existing type based on a logical predicate.

Purpose: To prove that a value conforms to certain rules at compile-time or safely at runtime.
Relationship: A `PositiveInt` is an `Int`, but an `Int` is not necessarily a `PositiveInt`. It represents a subset of the base type's values.
Scala 3 Implementation: Usually achieved via libraries using Scala 3 macros (like Iron or Refined).
```scala
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

// The type is an Int restricted to values > 0
type PositiveInt = Int :| Greater[0]

val x: PositiveInt = 5      // Compiles fine
// val y: PositiveInt = -1  // Compile error!
```
- Newtypes: Create a distinct type that is isomorphic to an existing type. They are used to add type safety without runtime overhead.

- Refined Types: Create a subtype of an existing type with additional constraints. They are used to enforce invariants and can have runtime overhead due to validation.

refined types are not newtypes. They are conceptually and structurally different, specifically in how they handle subtyping.
1. Newtypes have NO subtyping relationship
A newtype creates a completely independent, disjoint type. The compiler forgets the relationship and treats them as incompatible.

If you have a newtype UserId (backed by a String), you cannot pass a UserId to a function that expects a String.

2. Refined types DO have a subtyping relationship

Refined types DO have a subtyping relationship
A refined type creates a restricted subset of an existing type. It retains its identity as the base type.

If you have a refined type PositiveInt (which is an Int > 0), you can pass a PositiveInt to any regular function that mathematically expects an Int (like def add(a: Int, b: Int))


```scala
opaque type IronType[A, C] <: A = A

// Alias — resembles mathematical set-builder notation {x ∈ R | x > 0}
type :|[A, C] = IronType[A, C]
```

`A` — the base type (e.g., Int, Double, String)
`C` — the constraint/predicate (e.g., `Positive`, `Greater[0]`, `Not[Empty]`)
Because it's an opaque alias of `A`, refined types are zero-overhead at runtime — they desugar to the raw type.
`IronType[A, C]` is a subtype of `A`, so `Int :| Positive` can be used anywhere an `Int` is expected
```scala
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

type Temperature = Temperature.T
object Temperature extends RefinedType[Double, Positive]

type Speed = Speed.T
object Speed extends RefinedType[Double, Positive]
```

Now `Temperature` and `Speed` are distinct types even though both wrap `Double :| Positive`

```scala
//Refined Types — IronType[A, C] / A :| C
val x: Int :| Positive = 5
val y: Int :| Greater[0] = 10

// Both are still Int — subtyping flows freely
def add(a: Int, b: Int): Int = a + b
add(x, y) // ✅
```
```scala
//New Types — RefinedType[A, C]
type Temperature = Temperature.T
object Temperature extends RefinedType[Double, Positive]

type Speed = Speed.T
object Speed extends RefinedType[Double, Positive]

def setTemp(t: Temperature): Unit = ???
setTemp(Speed(100)) // ❌ Won't compile — distinct types
```

```sh
Level 0: inline vals/defs
│  Constant folding, dead branch elimination
│  Tools: inline, @inline, transparent inline
│
Level 1: compiletime utilities
│  Type-level computation, conditional summoning
│  Tools: constValue, summonInline, erasedValue, S[N] (Peano arithmetic)
│
Level 2: Quotes / Splices  (scala.quoted.Quotes)
│  Typed AST manipulation, code generation
│  Tools: '{}, ${}, Expr[T], Type[T], Varargs, Expr.ofList
│
Level 3: quotes.reflect  (TASTy API)
│  Full AST inspection: Terms, Types, Symbols, Flags
│  Tools: TypeRepr, Symbol, DefDef, ValDef, ClassDef, report
│
Level 4: Mirror + derives
│  Structural type information for generic programming
│  Tools: Mirror.ProductOf, Mirror.SumOf, MirroredElemLabels
│
Level 5: MacroAnnotation (experimental)
   Definition transformation — add/rewrite classes and defs
   Tools: MacroAnnotation, Symbol.newClass, Symbol.newMethod
```

Metaprogramming is writing code that treats other code as data — programs that read, generate, analyze, or transform programs.

## Worked examples

Each example lives in its own package under [src/main/scala/](src/main/scala/),
as a pair of files: the API (`Foo.scala`) and the implementation
(`FooMacros.scala`). They have to be separate — *a macro cannot be used in the
file that defines it*. Every one of them is exercised by a spec under
[src/test/scala/](src/test/scala/), including
[NegativeSpec.scala](src/test/scala/NegativeSpec.scala), which asserts on the
compile errors they produce.

| Example | Package | What it demonstrates | Level |
| --- | --- | --- | --- |
| **Optics** — `Lens.of[Person](_.address.city)` | [example.optics](src/main/scala/optics/) | Reading a *lambda's* tree: unwrap `Inlined`/`Closure`, walk the `Select` chain, synthesise nested `copy(...)` calls with `Apply`/`TypeApply` | 3 |
| **Compile-time DI** — `Wiring.wire[UserService]` | [example.wiring](src/main/scala/wiring/) | `Implicits.search` (the compiler's own resolution, run from a macro), constructor signatures via the `MethodType` chain, `New`/`Apply`, cycle detection, errors that name the wiring path | 3 |
| **Checked SQL** — `sql"... where id = $id"`, `Table.insertInto[T]` | [example.sql](src/main/scala/sql/) | Destructuring a `StringContext` with a quoted pattern, recovering an argument's static type after `Any*` widening, `Expr.summon` for type classes, compile-time lint, constant folding the whole statement | 2 |
| **Staged JSON encoder** — `JsonWriter.derive[A]` | [example.json](src/main/scala/json/) | Recursive *code generation* driven by type shape, quoted type patterns (`case '[Option[t]]`), reflective `Match` over sealed hierarchies, straight-line appends with no boxing and no intermediate collections | 2 + 3 |
| **Validated literals** — `uuid"..."`, `re"..."` | [example.literals](src/main/scala/literals/) | Parsing at compile time, and `Position(sourceFile, start, end)` to put the caret on the exact character *inside* a string literal that failed to parse | 3 |
| **Compile-time config** — `ServerConfig.parse("""{...}""")` | [example.config](src/main/scala/config/) | Running a real library (ujson) during compilation, validating *data* the way the compiler validates types, then `ToExpr` to lift the result back into a constructor call | 2 |
| **Staging / partial evaluation** — `Arith.compile(...)` | [example.staging](src/main/scala/staging/) | `FromExpr` (unlifting) and `ToExpr` (lifting) for a user type, generating N `val` bindings by recursing with nested quotes, unrolling a loop whose trip count is a compile-time constant | 2 |
| **Call-site metadata** — `SourceLoc.here`, `Trace.trace(x)` | [example.sourceloc](src/main/scala/sourceloc/) | `Position.ofMacroExpansion`, `Position.sourceCode` (the text the user actually typed), the `Symbol.spliceOwner` chain, and the `(using q: Quotes)(x: q.reflect.Symbol)` signature style | 3 |

### Two traps worth knowing

**`Quotes` instances are path-dependent.** `Term`, `TypeRepr` and `Symbol` belong
to *one* `Quotes`. Inside `'{ ... ${ ... } ... }` the splice runs under a *fresh*
one, so reflection done in there does not typecheck against reflection done
outside:

```
Found:    x$1.reflect.Symbol
Required: contextual$4.reflect.Symbol
```

The fix is always the same: do the reflective work before the quote, reduce it
to `Expr` values, and splice those in — `Expr` crosses the boundary freely.
See the comment in
[SourceLocMacros.scala](src/main/scala/sourceloc/SourceLocMacros.scala).

**Bindings inside a quote can shadow the macro's own variables.** In
`case Pow(base, _) => '{ val base = ${ generate(base, ...) }; ... }` the
generated `val base` captures the pattern variable, and the compiler reports
`Recursive value base needs type`. Name generated bindings so they cannot
collide.