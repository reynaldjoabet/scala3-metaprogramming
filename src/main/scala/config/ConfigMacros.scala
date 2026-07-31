package example.config

import scala.quoted.*

/**
  * Implementation of [[ServerConfig.parse]].
  *
  * Techniques on show:
  *   - running a real library (ujson) at compile time, over a literal obtained with `valueOrAbort`,
  *   - domain validation in the macro, so invalid *data* fails the build the same way invalid
  *     *types* do,
  *   - `ToExpr` for a user-defined type: the validated value is lifted back into a constructor
  *     call, which is what removes the runtime parse.
  */
private[config] object ConfigMacros {

  /**
    * Lifting a case class is mechanical: build the `apply` call and lift each field. `Expr(...)`
    * finds the stdlib `ToExpr` instances for `String`, `Int`, `Boolean` and `List`.
    */
  given serverConfigToExpr: ToExpr[ServerConfig] with {

    def apply(config: ServerConfig)(using Quotes): Expr[ServerConfig] =
      '{
        ServerConfig(
          ${ Expr(config.host) },
          ${ Expr(config.port) },
          ${ Expr(config.tls) },
          ${ Expr(config.origins) }
        )
      }

  }

  private val KnownKeys = Set("host", "port", "tls", "origins")

  def parseImpl(json: Expr[String])(using Quotes): Expr[ServerConfig] = {
    import quotes.reflect.*

    def fail(message: String): Nothing =
      report.errorAndAbort("Invalid server configuration: " + message, json)

    val text = json.valueOrAbort

    val root =
      try ujson.read(text)
      catch {
        case e: Throwable => fail("not valid JSON - " + e.getMessage)
      }

    val fields = root match {
      case obj: ujson.Obj => obj.value
      case other          => fail(s"expected a JSON object, found ${kindOf(other)}")
    }

    // Typos are the most common configuration bug, and here they are fatal at
    // compile time rather than silently ignored.
    fields.keys.filterNot(KnownKeys.contains).toList.sorted match {
      case Nil     => ()
      case unknown =>
        fail(
          s"unknown key(s) ${unknown.mkString(", ")}. Known keys: ${KnownKeys.toList.sorted.mkString(", ")}"
        )
    }

    def required(key: String): ujson.Value =
      fields.getOrElse(key, fail(s"missing required key `$key`"))

    val host = required("host") match {
      case s: ujson.Str if s.value.nonEmpty => s.value
      case _: ujson.Str                     => fail("`host` must not be empty")
      case other                            => fail(s"`host` must be a string, found ${kindOf(other)}")
    }

    val port = required("port") match {
      case n: ujson.Num if n.value.isWhole && n.value >= 1 && n.value <= 65535 =>
        n.value.toInt
      case n: ujson.Num =>
        fail(
          s"`port` must be a whole number between 1 and 65535, found ${n.value}"
        )
      case other => fail(s"`port` must be a number, found ${kindOf(other)}")
    }

    val tls = required("tls") match {
      case b: ujson.Bool => b.value
      case other         => fail(s"`tls` must be a boolean, found ${kindOf(other)}")
    }

    val origins = fields.get("origins") match {
      case None                 => Nil
      case Some(arr: ujson.Arr) =>
        arr.value.toList.map {
          case s: ujson.Str => s.value
          case other        =>
            fail(s"`origins` must contain strings, found ${kindOf(other)}")
        }
      case Some(other) =>
        fail(s"`origins` must be an array, found ${kindOf(other)}")
    }

    // One `ToExpr` call turns the validated value back into code.
    Expr(ServerConfig(host, port, tls, origins))
  }

  private def kindOf(value: ujson.Value): String = value match {
    case _: ujson.Str  => "a string"
    case _: ujson.Num  => "a number"
    case _: ujson.Bool => "a boolean"
    case _: ujson.Arr  => "an array"
    case _: ujson.Obj  => "an object"
    case _             => "null"
  }

}
