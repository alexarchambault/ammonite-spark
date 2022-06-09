
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
  .disablePlugins(MimaPlugin)
  .underModules
  .settings(
    shared,
    libraryDependencies += Deps.sparkSql.value % Provided
  )

lazy val `spark-stubs_30` = project
  .disablePlugins(MimaPlugin)
  .underModules
  .settings(
    shared,
    libraryDependencies += Deps.sparkSql3 % Provided
  )

lazy val `spark-stubs_32` = project
  .disablePlugins(MimaPlugin)
  .underModules
  .settings(
    shared,
    crossScalaVersions += Deps.Scala.scala213,
    libraryDependencies += Deps.sparkSql32 % Provided
  )

lazy val core = project
  .in(file("modules/core"))
  .settings(
    shared,
    crossScalaVersions += Deps.Scala.scala213,
    name := "ammonite-spark",
    Mima.settings,
    generatePropertyFile("org/apache/spark/sql/ammonitesparkinternals/ammonite-spark.properties"),
    libraryDependencies ++= Seq(
      Deps.ammoniteReplApi % Provided,
      Deps.sparkSql.value % Provided,
      Deps.jettyServer
    )
  )

lazy val tests = project
  .disablePlugins(MimaPlugin)
  .underModules
  .settings(
    shared,
    crossScalaVersions += Deps.Scala.scala213,
    skip.in(publish) := true,
    generatePropertyFile("ammonite/ammonite-spark.properties"),
    generateDependenciesFile,
    testSettings,
    libraryDependencies ++= Seq(
      Deps.ammoniteCompiler.exclude("com.google.guava", "guava"),
      Deps.ammoniteRepl.exclude("com.google.guava", "guava"),
      Deps.utest
    )
  )

lazy val `local-spark-distrib-tests` = project
  .disablePlugins(MimaPlugin)
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    testSettings
  )

lazy val `standalone-tests` = project
  .disablePlugins(MimaPlugin)
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    testSettings
  )

lazy val `yarn-tests` = project
  .disablePlugins(MimaPlugin)
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    crossScalaVersions += Deps.Scala.scala213,
    skip.in(publish) := true,
    testSettings
  )

lazy val `yarn-spark-distrib-tests` = project
  .disablePlugins(MimaPlugin)
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    skip.in(publish) := true,
    testSettings
  )

lazy val `ammonite-spark` = project
  .disablePlugins(MimaPlugin)
  .in(file("."))
  .aggregate(
    core,
    `spark-stubs_24`,
    `spark-stubs_30`,
    `spark-stubs_32`,
    tests
  )
  .settings(
    crossScalaVersions := Nil,
    skip.in(publish) := true
  )
