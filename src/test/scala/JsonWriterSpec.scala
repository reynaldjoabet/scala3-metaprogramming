import example.json.JsonWriter
import munit.FunSuite

object model {
  final case class Point(x: Int, y: Int)
  final case class Team(name: String, size: Int, active: Boolean, ratio: Double)
  final case class Profile(nickname: Option[String], tags: List[String])
  final case class Nested(point: Point, points: List[Point])
  final case class Scores(byPlayer: Map[String, Int])

  enum Shape {
    case Circle(radius: Double)
    case Rect(width: Double, height: Double)
    case Empty
  }

  sealed trait Event
  final case class Login(user: String) extends Event
  final case class Click(x: Int, y: Int) extends Event

  final case class Money(cents: Long)
  final case class Invoice(total: Money)
}

class JsonWriterSpec extends FunSuite {
  import model.*

  test("case class") {
    val w = JsonWriter.derive[Point]
    assertEquals(w.toJson(Point(1, 2)), """{"x":1,"y":2}""")
  }

  test("primitives keep their own append overloads") {
    val w = JsonWriter.derive[Team]
    assertEquals(
      w.toJson(Team("ops", 4, true, 0.5)),
      """{"name":"ops","size":4,"active":true,"ratio":0.5}"""
    )
  }

  test("strings are escaped") {
    val w = JsonWriter.derive[Point]
    val s = JsonWriter.derive[Team]
    assertEquals(
      s.toJson(Team("a\"b\nc", 0, false, 0.0)),
      """{"name":"a\"b\nc","size":0,"active":false,"ratio":0.0}"""
    )
    assertEquals(w.toJson(Point(0, 0)), """{"x":0,"y":0}""")
  }

  test("Option and collections") {
    val w = JsonWriter.derive[Profile]
    assertEquals(
      w.toJson(Profile(Some("ada"), List("a", "b"))),
      """{"nickname":"ada","tags":["a","b"]}"""
    )
    assertEquals(
      w.toJson(Profile(None, Nil)),
      """{"nickname":null,"tags":[]}"""
    )
  }

  test("nested case classes are inlined, not delegated") {
    val w = JsonWriter.derive[Nested]
    assertEquals(
      w.toJson(Nested(Point(1, 2), List(Point(3, 4)))),
      """{"point":{"x":1,"y":2},"points":[{"x":3,"y":4}]}"""
    )
  }

  test("Map[String, *]") {
    val w = JsonWriter.derive[Scores]
    assertEquals(
      w.toJson(Scores(Map("ada" -> 3))),
      """{"byPlayer":{"ada":3}}"""
    )
  }

  test("enums get a type discriminator") {
    val w = JsonWriter.derive[Shape]
    assertEquals(
      w.toJson(Shape.Circle(1.5)),
      """{"type":"Circle","radius":1.5}"""
    )
    assertEquals(
      w.toJson(Shape.Rect(2.0, 3.0)),
      """{"type":"Rect","width":2.0,"height":3.0}"""
    )
    assertEquals(w.toJson(Shape.Empty), """{"type":"Empty"}""")
  }

  test("sealed traits dispatch through a generated match") {
    val w = JsonWriter.derive[Event]
    assertEquals(w.toJson(Login("ada")), """{"type":"Login","user":"ada"}""")
    assertEquals(w.toJson(Click(1, 2)), """{"type":"Click","x":1,"y":2}""")
  }

  test("a given instance overrides the structural encoding of a nested type") {
    given JsonWriter[Money] with {
      def write(value: Money, sb: java.lang.StringBuilder): Unit = {
        sb.append(value.cents / 100.0)
        ()
      }
    }

    val w = JsonWriter.derive[Invoice]
    assertEquals(w.toJson(Invoice(Money(1250))), """{"total":12.5}""")
  }

  test("top level collections and primitives") {
    assertEquals(JsonWriter.derive[List[Int]].toJson(List(1, 2, 3)), "[1,2,3]")
    assertEquals(JsonWriter.derive[String].toJson("hi"), "\"hi\"")
    assertEquals(
      JsonWriter.derive[Array[Boolean]].toJson(Array(true, false)),
      "[true,false]"
    )
  }

  // Does not compile:
  //   final case class Tree(children: List[Tree])
  //   JsonWriter.derive[Tree]
  //     => Tree is recursive. Provide a `given JsonWriter[Tree]` to break the cycle.
}
