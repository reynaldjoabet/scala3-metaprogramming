package example.wiring

/** Constructor injection resolved entirely at compile time.
  *
  * `Wiring.wire[UserService]` inspects the primary constructor of
  * `UserService`, resolves every parameter - preferring a `given` in scope,
  * otherwise recursively constructing it - and expands to a plain `new` tree:
  *
  * {{{
  * // given conn: Connection
  * val svc = Wiring.wire[UserService]
  * // expands to: new UserService(new UserRepo(conn), new Clock())
  * }}}
  *
  * The whole object graph is a single allocation-free-of-surprises expression:
  * no reflection, no runtime container, no `Map[Class[?], Any]`, and a missing
  * dependency is a *compile error* that names the wiring path that needed it.
  * This is what MacWire does.
  *
  * @see
  *   [[WiringMacros]] for the implementation.
  */
object Wiring {

  /** Build an `A` by recursively resolving its constructor parameters. */
  inline def wire[A]: A = ${ WiringMacros.wireImpl[A] }

  /** Like [[wire]], but reports the generated tree as a compile-time `info`
    * message - handy when you want to see what the container actually built.
    */
  inline def wireDebug[A]: A = ${ WiringMacros.wireDebugImpl[A] }
}
