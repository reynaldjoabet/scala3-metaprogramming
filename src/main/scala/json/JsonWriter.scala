package example.json

/** A JSON encoder whose body is generated as *straight-line code* at compile
  * time.
  *
  * `JsonWriter.derive[Person]` does not build a `Map`, a list of field
  * accessors, or an intermediate AST. It expands to the appends themselves:
  *
  * {{{
  * sb.append("{\"name\":"); JsonWriter.writeString(value.name, sb)
  * sb.append(",\"age\":");  sb.append(value.age)
  * sb.append("}")
  * }}}
  *
  * That is the difference between "derivation" and *staged* derivation: field
  * names become string constants in the constant pool, the separators are
  * folded into those constants, dispatch is monomorphic, and an `Int` field is
  * appended through `java.lang.StringBuilder.append(int)` with no boxing. It is
  * the strategy jsoniter-scala uses to beat reflective encoders by an order of
  * magnitude.
  *
  * Supported out of the box: the primitives, `String`, `UUID`, `Option`,
  * `Array`, any `Iterable`, `Map[String, *]`, case classes, and sealed
  * hierarchies / enums (encoded with a `"type"` discriminator).
  *
  * A `given JsonWriter[X]` in scope always wins for a nested `X`, which is how
  * you plug in a custom encoding or break a recursive type.
  *
  * @see
  *   [[JsonWriterMacros]] for the implementation.
  */
trait JsonWriter[A] {

  /** `java.lang.StringBuilder`, not `scala.StringBuilder`: the Java one has
    * primitive `append` overloads, so generated code never boxes.
    */
  def write(value: A, sb: java.lang.StringBuilder): Unit

  final def toJson(value: A): String = {
    val sb = new java.lang.StringBuilder
    write(value, sb)
    sb.toString
  }
}

object JsonWriter {

  /** Note this is a plain `inline def` and *not* a `given`: that is what lets
    * the macro use `Expr.summon[JsonWriter[X]]` to look for a user-supplied
    * instance without immediately finding itself.
    */
  inline def derive[A]: JsonWriter[A] = ${ JsonWriterMacros.deriveImpl[A] }

  /** Called by generated code - the one piece that is worth a real method
    * rather than more inlined bytecode.
    */
  def writeString(value: String, sb: java.lang.StringBuilder): Unit = {
    sb.append('"')
    var i = 0
    while (i < value.length) {
      val c = value.charAt(i)
      c match {
        case '"'          => sb.append("\\\"")
        case '\\'         => sb.append("\\\\")
        case '\n'         => sb.append("\\n")
        case '\r'         => sb.append("\\r")
        case '\t'         => sb.append("\\t")
        case _ if c < ' ' => sb.append("\\u%04x".format(c.toInt))
        case _            => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
    ()
  }
}
