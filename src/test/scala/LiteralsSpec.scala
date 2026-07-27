import example.literals.*
import munit.FunSuite

class LiteralsSpec extends FunSuite {

  test("uuid literal is decoded at compile time") {
    val id = uuid"6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    assertEquals(id.toString, "6ba7b810-9dad-11d1-80b4-00c04fd430c8")
    // The expansion is `new UUID(hi, lo)`, so these are the constants the
    // macro computed.
    assertEquals(id.getMostSignificantBits, 0x6ba7b8109dad11d1L)
    assertEquals(id.getLeastSignificantBits, 0x80b400c04fd430c8L)
  }

  test("regex literal is validated at compile time") {
    val slug = re"^[a-z0-9-]+$$"
    assert(slug.matcher("hello-42").matches())
    assert(!slug.matcher("Hello 42").matches())
  }

  test("regex literals keep their backslashes") {
    val digits = re"\d{3}-\d{4}"
    assert(digits.matcher("555-1234").matches())
  }

  // None of these compile:
  //   uuid"not-a-uuid"          => Not a canonical UUID
  //   uuid"1-2-3-4-5"           => Not a canonical UUID (UUID.fromString accepts it!)
  //   re"^[a-z"                 => Unclosed character class, with the caret on the `[`
  //   val p = "a"; re"$p"       => a re literal cannot contain interpolated values
}
