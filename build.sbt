import Dependencies._

ThisBuild / scalaVersion       := "3.9.0"
ThisBuild / version            := "0.1.0-SNAPSHOT"
ThisBuild / crossScalaVersions := Seq("3.3.8", "3.9.0")

ThisBuild / scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-java-output-version:17",
  "-Werror",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wshadow:all",
  "-Xcheck-macros",
  "-Xmax-inlines:64"
)

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file("."))
  .settings(
    name                := "Scala3-Metaprogramming",
    libraryDependencies += munit % Test
  )

libraryDependencies += "com.lihaoyi" %% "ujson" % "4.4.3"
