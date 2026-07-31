package example.config

/**
  * Configuration that is parsed, validated and typed *by the compiler*.
  *
  * {{{
  * val config = ServerConfig.parse("""
  *   { "host": "0.0.0.0", "port": 8080, "tls": true }
  * """)
  * }}}
  *
  * expands to
  *
  * {{{
  * ServerConfig("0.0.0.0", 8080, true, Nil)
  * }}}
  *
  * A missing key, a misspelled key, a port of `70000` or a string where a number belongs is a
  * *compile error*, not a `NoSuchElementException` during the first boot after a deploy. And
  * because the result is lifted back into a constructor call, no JSON parser runs at startup - the
  * parsing happened once on the build machine.
  *
  * The same shape works for a config *file*: read it with `Source.fromInputStream` inside the
  * macro, exactly as [[JsonModelMacros]] does, and the file becomes a build-time input.
  *
  * @see
  *   [[ConfigMacros]] for the implementation.
  */
final case class ServerConfig(
    host: String,
    port: Int,
    tls: Boolean,
    origins: List[String]
)

object ServerConfig {

  /**
    * Parse and validate a JSON literal at compile time.
    */
  inline def parse(inline json: String): ServerConfig =
    ${ ConfigMacros.parseImpl('json) }

}
