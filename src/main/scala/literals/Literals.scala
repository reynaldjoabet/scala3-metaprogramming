package example.literals

/** Literals that are parsed and checked by the compiler instead of at startup.
  *
  * {{{
  * val id = uuid"6ba7b810-9dad-11d1-80b4-00c04fd430c8"
  * val slug = re"^[a-z0-9-]+$$"
  * }}}
  *
  * Two separate wins:
  *
  *   - '''validation moves left'''. A malformed UUID or regex is a compile
  *     error pointing at the exact character that broke, not a
  *     `PatternSyntaxException` thrown the first time that code path runs in
  *     production.
  *   - '''parsing disappears'''. `uuid"..."` does not expand to
  *     `UUID.fromString("...")`; it expands to `new UUID(hi, lo)` with both
  *     halves already computed, so the runtime cost is one allocation and the
  *     string never even reaches the class file.
  *
  * @see
  *   [[LiteralMacros]] for the implementation.
  */
extension (inline sc: StringContext) {

  /** A UUID literal, verified and pre-decoded at compile time. */
  inline def uuid(inline args: Any*): java.util.UUID =
    ${ LiteralMacros.uuidImpl('sc, 'args) }

  /** A regex literal, compiled once at compile time to prove it parses. */
  inline def re(inline args: Any*): java.util.regex.Pattern =
    ${ LiteralMacros.regexImpl('sc, 'args) }
}
