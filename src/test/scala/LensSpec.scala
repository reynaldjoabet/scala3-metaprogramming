import example.optics.Lens
import munit.FunSuite

final case class Street(name: String, number: Int)
final case class Address(street: Street, city: String)
final case class Person(name: String, age: Int, address: Address)
final case class Box[A](value: A, label: String)

class LensSpec extends FunSuite {

  private val person =
    Person("Ada", 36, Address(Street("Marconi", 12), "Torino"))

  test("single field lens gets and replaces") {
    val name = Lens.of[Person](_.name)
    assertEquals(name.get(person), "Ada")
    assertEquals(name.replace("Grace")(person).name, "Grace")
    // everything else is untouched
    assertEquals(name.replace("Grace")(person).address, person.address)
  }

  test("nested path expands to a chain of copies") {
    val city = Lens.of[Person](_.address.city)
    assertEquals(city.get(person), "Torino")
    assertEquals(city.replace("Milano")(person).address.city, "Milano")
    assertEquals(
      city.replace("Milano")(person).address.street,
      person.address.street
    )
  }

  test("three levels deep") {
    val streetNumber = Lens.of[Person](_.address.street.number)
    assertEquals(streetNumber.get(person), 12)
    assertEquals(streetNumber.modify(_ + 1)(person).address.street.number, 13)
  }

  test("generic case classes keep their type arguments") {
    val value = Lens.of[Box[Int]](_.value)
    assertEquals(value.get(Box(1, "one")), 1)
    assertEquals(value.replace(2)(Box(1, "one")), Box(2, "one"))
  }

  test("lenses compose") {
    val address          = Lens.of[Person](_.address)
    val street           = Lens.of[Address](_.street)
    val name             = Lens.of[Street](_.name)
    val personStreetName = address.andThen(street).andThen(name)

    assertEquals(personStreetName.get(person), "Marconi")
    assertEquals(
      personStreetName.replace("Garibaldi")(person).address.street.name,
      "Garibaldi"
    )
  }

  test("modify is get + replace") {
    val age = Lens.of[Person](_.age)
    assertEquals(age.modify(_ * 2)(person).age, 72)
  }

  // Does not compile - the macro rejects it at the offending selection:
  //   Lens.of[Person](_.name.toUpperCase)
  //   Lens.of[Person](p => if (p.age > 18) p.name else "")
}
