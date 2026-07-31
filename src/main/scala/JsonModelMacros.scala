package example

import scala.collection.mutable
import scala.quoted.*

import ujson.Null as JsonNull

object JsonModelMacros {

  inline def caseClassesFromResource(
      inline resourcePath: String,
      inline rootTypeName: String
  ): String =
    ${ caseClassesFromResourceImpl('resourcePath, 'rootTypeName) }

  private def caseClassesFromResourceImpl(
      resourcePathExpr: Expr[String],
      rootTypeNameExpr: Expr[String]
  )(using Quotes): Expr[String] = {
    import quotes.reflect.*

    val resourcePath = resourcePathExpr.valueOrAbort
    val rootTypeName = rootTypeNameExpr.valueOrAbort

    val cl: ClassLoader = {
      val tcl = Thread.currentThread().nn.getContextClassLoader
      if (tcl != null) tcl
      else classOf[JsonModelMacros.type].getClassLoader.nn
    }

    val stream: java.io.InputStream = {
      val s = cl.getResourceAsStream(resourcePath)
      if (s != null) s
      else report.errorAndAbort("Resource not found: " + resourcePath)
    }

    val json =
      try
        scala.io.Source.fromInputStream(stream, "UTF-8").mkString
      finally
        stream.close()

    val root =
      try
        ujson.read(json)
      catch {
        case e: Throwable =>
          report.errorAndAbort(
            "Invalid JSON in " + resourcePath + ": " + e.getMessage
          )
      }

    val fullString = generate(rootTypeName, root)

    // JVM constant pool limit is 65535 bytes per UTF-8 string entry.
    // Split into safe chunks and concatenate at runtime.
    val ChunkSize = 60000
    if (fullString.length <= ChunkSize) {
      Expr(fullString)
    } else {
      val chunks                         = fullString.grouped(ChunkSize).toList
      val chunkExprs: List[Expr[String]] = chunks.map(Expr(_))
      '{ ${ Expr.ofList(chunkExprs) }.mkString }
    }
  }

  private def generate(rootTypeName: String, root: ujson.Value): String = {
    val emitted = mutable.LinkedHashMap.empty[String, String]

    def pascal(s: String): String = {
      val p = s
        .split("[^A-Za-z0-9]+")
        .nn
        .map(_.nn)
        .filter(_.nonEmpty)
        .map(part => part.head.toUpper + part.drop(1))
        .mkString
      if (p.isEmpty) {
        "Anonymous"
      } else {
        p
      }
    }

    def safeField(s: String): String = {
      val cleaned0 = s.replaceAll("[^A-Za-z0-9_]", "_").nn
      val cleaned  = if (cleaned0.isEmpty) {
        "field"
      } else if (cleaned0.head.isDigit) {
        "_" + cleaned0
      } else {
        cleaned0
      }

      cleaned match {
        case "type" | "match" | "object" | "val" | "var" | "def" =>
          "`" + cleaned + "`"
        case _ => cleaned
      }
    }

    def inferType(
        fieldName: String,
        value: ujson.Value,
        owner: String
    ): String = {
      value match {
        case _: ujson.Str     => "String"
        case _: ujson.Bool    => "Boolean"
        case n: ujson.Num     => if (n.value.isWhole) "Long" else "Double"
        case _: JsonNull.type => "Option[String]"
        case obj: ujson.Obj   =>
          val nested = owner + pascal(fieldName)
          emitCaseClass(nested, obj)
          nested
        case arr: ujson.Arr =>
          val elemType =
            arr.value.find(v => !v.isInstanceOf[JsonNull.type]) match {
              case Some(v) => inferType(fieldName + "Item", v, owner)
              case None    => "String"
            }
          "List[" + elemType + "]"
      }
    }

    def emitCaseClass(name: String, obj: ujson.Obj): Unit = {
      if (!emitted.contains(name)) {
        val fields = obj.value.toSeq
          .map { case (k, v) =>
            val tpe = inferType(k, v, name)
            safeField(k) + ": " + tpe
          }
          .mkString(", ")

        emitted(name) = "final case class " + name + "(" + fields + ")"
      }
    }

    root match {
      case o: ujson.Obj =>
        emitCaseClass(pascal(rootTypeName), o)
        emitted.values.mkString("\n")
      case _ =>
        "// Root must be a JSON object: " + rootTypeName
    }
  }

}
