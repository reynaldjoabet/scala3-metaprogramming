import example.config.ServerConfig
import munit.FunSuite

class ConfigSpec extends FunSuite {

  test("a valid config is parsed at compile time and lifted back into code") {
    val config = ServerConfig.parse("""
      {
        "host": "0.0.0.0",
        "port": 8080,
        "tls": true,
        "origins": ["https://a.example", "https://b.example"]
      }
    """)

    assertEquals(
      config,
      ServerConfig(
        "0.0.0.0",
        8080,
        true,
        List("https://a.example", "https://b.example")
      )
    )
  }

  test("optional keys fall back to their default") {
    val config =
      ServerConfig.parse("""{"host":"localhost","port":80,"tls":false}""")
    assertEquals(config.origins, Nil)
  }

  // None of these compile:
  //   ServerConfig.parse("""{"host":"h","port":70000,"tls":true}""")
  //     => `port` must be a whole number between 1 and 65535, found 70000.0
  //   ServerConfig.parse("""{"host":"h","port":80,"tsl":true}""")
  //     => unknown key(s) tsl. Known keys: host, origins, port, tls
  //   ServerConfig.parse("""{"host":"h","port":"80","tls":true}""")
  //     => `port` must be a number, found a string
  //   ServerConfig.parse("""{"host":"h"}""")
  //     => missing required key `port`
}
