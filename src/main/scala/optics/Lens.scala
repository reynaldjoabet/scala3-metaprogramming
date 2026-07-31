package example.optics

/**
  * A first-class getter/setter pair for one field (or one *path* of fields) of an immutable
  * structure.
  *
  * `Lens.of[Person](_.address.city)` expands - at compile time - into exactly the code you would
  * have written by hand:
  *
  * {{{
  * new Lens[Person, String](
  *   s => s.address.city,
  *   c => s => s.copy(address = s.address.copy(city = c))
  * )
  * }}}
  *
  * No runtime reflection, no string-keyed field lookup, no `Dynamic`. The macro's whole job is to
  * turn a *lambda that the compiler has already type-checked* into a `copy` chain, which is why the
  * path is checked at compile time: `_.address.zip.toUpperCase` is rejected with an error at the
  * offending selection, not at runtime.
  *
  * This is the technique behind Monocle's `GenLens` and quicklens' `modify`.
  *
  * @see
  *   [[LensMacros]] for the implementation - the macro *definition* and its *use* must live in
  *   different files.
  */
final class Lens[S, A](val get: S => A, val replace: A => S => S) {

  /**
    * Apply a function to the focused field, rebuilding the outer structure.
    */
  def modify(f: A => A): S => S = s => replace(f(get(s)))(s)

  /**
    * Lens composition: `personCity = personAddress.andThen(addressCity)`.
    */
  def andThen[B](that: Lens[A, B]): Lens[S, B] =
    new Lens[S, B](
      s => that.get(get(s)),
      b => s => modify(that.replace(b))(s)
    )

}

object Lens {

  /**
    * `Lens.of[Person](_.address.city)`.
    *
    * The builder exists purely for partial type application: we want to *give* `S` and *infer* `A`
    * from the lambda, and Scala has no syntax for supplying only some type arguments.
    */
  def of[S]: LensBuilder[S] = builder.asInstanceOf[LensBuilder[S]]

  private val builder = new LensBuilder[Any]

}

final class LensBuilder[S] {

  /**
    * `inline` on the parameter is what makes this work: it hands the macro the *syntax tree* of the
    * lambda instead of a compiled function object.
    */
  inline def apply[A](inline path: S => A): Lens[S, A] =
    ${ LensMacros.lensImpl[S, A]('path) }

}
