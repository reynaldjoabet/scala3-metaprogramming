import Dependencies._

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "Scala3-Metaprogramming",
    libraryDependencies += munit % Test
  )

ThisBuild / scalacOptions ++= Seq(
  "-no-indent",
  // "-deprecation", // Warns about deprecated APIs
  "-feature", // Warns about advanced language features
  // "-unchecked",//[warn] Flag -unchecked set repeatedly
  // "-Wunused:imports",
  //   "-Wunused:privates",
  //   "-Wunused:locals",
  //   "-Wunused:explicits",
  //   "-Wunused:implicits",
  //   "-Wunused:params",
  //   "-Wvalue-discard",
  // "-language:strictEquality",
  "-Xmax-inlines:100000",
  "-Yexplicit-nulls"
)

libraryDependencies += "com.lihaoyi" %% "ujson" % "4.4.3"
