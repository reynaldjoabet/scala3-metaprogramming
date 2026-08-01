package example.json

import scala.quoted.*

/**
  * Implementation of [[JsonWriter.derive]].
  *
  * Techniques on show:
  *   - a recursive code *generator* (not a recursive runtime function): the shape of the type
  *     drives which quotes get stitched together,
  *   - quoted type patterns (`case '[Option[t]] =>`) to destructure a `TypeRepr` and bring `t` into
  *     scope as a real `Type`,
  *   - `Expr.summon` for user overrides, with the root type excluded so a
  *     `given w: JsonWriter[X] = JsonWriter.derive` cannot summon itself,
  *   - `Select(value, field)` to read case class fields, and a reflective `Match` over `TypeTest`
  *     patterns for sealed hierarchies,
  *   - an explicit stack of enclosing types, which turns "recursive type" from a compiler stack
  *     overflow into a readable error.
  *
  * Everything lives in nested `def`s so that `quotes.reflect` types (`Term`, `TypeRepr`) stay tied
  * to one stable `Quotes` instance - the alternative is threading `(using q: Quotes)` and writing
  * `q.reflect.Term` everywhere.
  */
private[json] object JsonWriterMacros {

  /**
    * The field used to tag which branch of a sealed hierarchy was encoded.
    */
  private val Discriminator = "type"

  def deriveImpl[A: Type](using Quotes): Expr[JsonWriter[A]] = {
    import quotes.reflect.*

    type Sink = java.lang.StringBuilder

    def fail(msg: String, stack: List[TypeRepr]): Nothing = {
      val where =
        if (stack.isEmpty) ""
        else "\n  while deriving: " + stack.reverse.map(_.show).mkString(" -> ")
      report.errorAndAbort(msg + where, Position.ofMacroExpansion)
    }

    def append(sink: Term, text: String): Expr[Unit] = {
      val sb = sink.asExprOf[Sink]
      '{ $sb.append(${ Expr(text) }); () }
    }

    /**
      * Generate the code that writes `value` (a term of type `tpe`) into `sink`.
      */
    def writeValue(
        tpe: TypeRepr,
        value: Term,
        sink: Term,
        stack: List[TypeRepr]
    ): Expr[Unit] = {
      val sb      = sink.asExprOf[Sink]
      val widened = tpe.widen.dealias

      def is[T: Type]: Boolean = widened =:= TypeRepr.of[T]

      // 1. A user-written given always wins for nested types. The root is
      //    excluded: `given w: JsonWriter[P] = JsonWriter.derive` would
      //    otherwise expand into an instance that delegates to itself.
      val userInstance: Option[Expr[Unit]] =
        if (stack.isEmpty) None
        else {
          widened.asType match {
            case '[t] =>
              Expr
                .summon[JsonWriter[t]]
                .map(w => '{ $w.write(${ value.asExprOf[t] }, $sb) })
          }
        }

      userInstance.getOrElse {
        // 2. Primitives: appended through the matching overload, no boxing.
        if (is[String]) {
          '{ JsonWriter.writeString(${ value.asExprOf[String] }, $sb) }
        } else if (is[Int]) {
          '{ $sb.append(${ value.asExprOf[Int] }); () }
        } else if (is[Long]) {
          '{ $sb.append(${ value.asExprOf[Long] }); () }
        } else if (is[Double]) {
          '{ $sb.append(${ value.asExprOf[Double] }); () }
        } else if (is[Float]) {
          '{ $sb.append(${ value.asExprOf[Float] }); () }
        } else if (is[Boolean]) {
          '{ $sb.append(${ value.asExprOf[Boolean] }); () }
        } else if (is[Short]) {
          '{ $sb.append(${ value.asExprOf[Short] }.toInt); () }
        } else if (is[Byte]) {
          '{ $sb.append(${ value.asExprOf[Byte] }.toInt); () }
        } else if (is[Char]) {
          '{ JsonWriter.writeString(${ value.asExprOf[Char] }.toString, $sb) }
        } else if (is[java.util.UUID]) {
          '{
            JsonWriter.writeString(
              ${ value.asExprOf[java.util.UUID] }.toString,
              $sb
            )
          }
        } else if (stack.exists(_ =:= widened)) {
          fail(
            s"${widened.show} is recursive. Provide a `given JsonWriter[${widened.show}]` " +
              "to break the cycle (a lazy val works).",
            stack
          )
        } else {
          // 3. Containers, then structure.
          widened.asType match {
            case '[Option[t]] =>
              '{
                ${ value.asExprOf[Option[t]] } match {
                  case Some(inner) =>
                    ${
                      writeValue(TypeRepr.of[t], 'inner.asTerm, sink, stack)
                    }
                  case None => ${ append(sink, "null") }
                }
              }

            case '[Map[String, t]] =>
              '{
                val it = ${ value.asExprOf[Map[String, t]] }.iterator
                $sb.append("{")
                var first = true
                while (it.hasNext) {
                  val entry = it.next()
                  if (!first) { $sb.append(",") }
                  first = false
                  JsonWriter.writeString(entry._1, $sb)
                  $sb.append(":")
                  ${
                    writeValue(TypeRepr.of[t], '{ entry._2 }.asTerm, sink, stack)
                  }
                }
                $sb.append("}")
                ()
              }

            case '[Array[t]] =>
              '{
                val array = ${ value.asExprOf[Array[t]] }
                $sb.append("[")
                var i = 0
                while (i < array.length) {
                  if (i > 0) { $sb.append(",") }
                  val element = array(i)
                  ${
                    writeValue(TypeRepr.of[t], 'element.asTerm, sink, stack)
                  }
                  i += 1
                }
                $sb.append("]")
                ()
              }

            case '[Iterable[t]] =>
              '{
                val it = ${ value.asExprOf[Iterable[t]] }.iterator
                $sb.append("[")
                var first = true
                while (it.hasNext) {
                  if (!first) { $sb.append(",") }
                  first = false
                  val element = it.next()
                  ${
                    writeValue(TypeRepr.of[t], 'element.asTerm, sink, stack)
                  }
                }
                $sb.append("]")
                ()
              }

            case _ =>
              val sym = widened.typeSymbol
              // Sums first: a sealed parent has children, a case class never
              // does - and testing this way also lets a single enum *case*
              // (which carries both the Case and Enum flags) be encoded on its
              // own.
              if (sym.children.nonEmpty) {
                writeSum(widened, value, sink, stack)
              } else if (sym.flags.is(Flags.Case)) {
                writeProduct(widened, value, sink, stack, None)
              } else {
                fail(
                  s"Cannot encode ${widened.show}: it is not a primitive, a collection, " +
                    "a case class or a sealed hierarchy, and no given JsonWriter is in scope.",
                  stack
                )
              }
          }
        }
      }
    }

    /**
      * `{"a":1,"b":"x"}` - the braces, field names and separators are all constants folded into the
      * surrounding literals.
      */
    def writeProduct(
        tpe: TypeRepr,
        value: Term,
        sink: Term,
        stack: List[TypeRepr],
        tag: Option[String]
    ): Expr[Unit] = {
      val fields = tpe.typeSymbol.caseFields

      val opening = tag match {
        case Some(name) => "{\"" + Discriminator + "\":\"" + name + "\""
        case None       => "{"
      }

      val body = fields.zipWithIndex.map { case (field, i) =>
        val needsComma = i > 0 || tag.isDefined
        val prefix     = (if (needsComma) "," else "") + "\"" + field.name + "\":"
        '{
          ${ append(sink, prefix) }
          ${
            writeValue(
              tpe.memberType(field),
              // `value` is always a parameter or a local val, so repeating the
              // selection here is a field read, not a recomputation.
              Select(value, field),
              sink,
              tpe :: stack
            )
          }
        }
      }

      Expr.block(append(sink, opening) :: body, append(sink, "}"))
    }

    /**
      * `value match { case _: Dog => ...; case _: Cat => ... }`, built with the reflection API
      * because the branches are only known at expansion time.
      */
    def writeSum(
        tpe: TypeRepr,
        value: Term,
        sink: Term,
        stack: List[TypeRepr]
    ): Expr[Unit] = {
      if (tpe.typeArgs.nonEmpty) {
        fail(
          s"Generic sealed hierarchies are not supported: ${tpe.show}",
          stack
        )
      }

      val cases = tpe.typeSymbol.children.map { child =>
        // A parameterless enum case is a *term* (a singleton), a case class
        // child is a *type*. Both can be matched on with a type pattern.
        val childTpe = if (child.isTerm) child.termRef else child.typeRef

        childTpe.asType match {
          case '[c] =>
            val childTree = TypeTree.of[c]
            // Reuse the scrutinee rather than binding a pattern variable: it
            // saves synthesising a `Bind` symbol, and the cast is free.
            val narrowed =
              TypeApply(Select.unique(value, "asInstanceOf"), List(childTree))
            val body =
              writeProduct(
                childTpe,
                narrowed,
                sink,
                tpe :: stack,
                Some(child.name)
              )
            CaseDef(Typed(Wildcard(), childTree), None, body.asTerm)
        }
      }

      Match(value, cases).asExprOf[Unit]
    }

    '{
      new JsonWriter[A] {
        def write(value: A, sb: java.lang.StringBuilder): Unit =
          // Both arguments cross into the generators as `Term`s, not `Expr`s.
          // `'value` and `'sb` are created *inside* this splice, so they carry
          // its scope, while every generator above quotes with the `Quotes` of
          // the enclosing `deriveImpl`. Handing over the bare tree and letting
          // each generator re-wrap it (`sink.asExprOf[Sink]`) is what keeps the
          // two scopes from meeting.
          ${ writeValue(TypeRepr.of[A], 'value.asTerm, 'sb.asTerm, Nil) }
      }
    }
  }

}
