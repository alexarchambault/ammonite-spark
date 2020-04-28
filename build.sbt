
import Settings._

inThisBuild(List(
  organization := "sh.almond",
  homepage := Some(url("https://github.com/alexarchambault/ammonite-spark.git")),
  licenses := List("MIT" -> url("https://spdx.org/licenses/MIT.html")),
  developers := List(
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "alexandre.archambault@gmail.com",
      url("https://github.com/alexarchambault")
    )
  )
))

lazy val `spark-stubs_24` = project
  .underModules
  .settings(
    shared,
    libraryDependencies += Deps.sparkSql % Provided
  )

lazy val `spark-stubs_30` = project
  .underModules
  .settings(
    shared,
    libraryDependencies += Deps.sparkSql3 % Provided
  )

lazy val core = project
  .in(file("modules/core"))
  .settings(
    shared,
    name := "ammonite-spark",
    generatePropertyFile("org/apache/spark/sql/ammonitesparkinternals/ammonite-spark.properties"),
    libraryDependencies ++= Seq(
      Deps.ammoniteReplApi % Provided,
      Deps.sparkSql % Provided,
      Deps.jettyServer
    )
  )

lazy val tests = project
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    generatePropertyFile("ammonite/ammonite-spark.properties"),
    generateDependenciesFile,
    testSettings,
    libraryDependencies ++= Seq(
      Deps.ammoniteRepl.exclude("com.google.guava", "guava"),
      Deps.utest
    )
  )

lazy val `local-spark-distrib-tests` = project
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    testSettings
  )

lazy val `standalone-tests` = project
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    testSettings
  )

lazy val `yarn-tests` = project
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    testSettings
  )

lazy val `yarn-spark-distrib-tests` = project
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    testSettings
  )

lazy val `ammonite-spark` = project
  .in(file("."))
  .aggregate(
    core,
    `spark-stubs_24`,
    `spark-stubs_30`,
    tests
  )
  .settings(
    crossScalaVersions := Nil,
    skip.in(publish) := true
  )
