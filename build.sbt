
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

lazy val `spark-stubs_20` = project
  .underModules
  .settings(
    shared,
    baseDirectory := {
      val baseDir = baseDirectory.value

      if (Settings.isAtLeast212.value)
        baseDir / "target" / "dummy"
      else
        baseDir
    },
    libraryDependencies ++= {
      if (Settings.isAtLeast212.value)
        Nil
      else
        Seq(Deps.sparkSql20 % "provided")
    },
    publishArtifact := !Settings.isAtLeast212.value
  )

lazy val `spark-stubs_24` = project
  .underModules
  .settings(
    shared,
    libraryDependencies += Deps.sparkSql24 % "provided"
  )

lazy val core = project
  .in(file("modules/core"))
  .settings(
    shared,
    name := "ammonite-spark",
    generatePropertyFile("org/apache/spark/sql/ammonitesparkinternals/ammonite-spark.properties"),
    libraryDependencies ++= Seq(
      Deps.ammoniteRepl % "provided",
      Deps.sparkSql.value % "provided",
      Deps.jettyServer
    )
  )

lazy val tests = project
  .underModules
  .settings(
    shared,
    dontPublish,
    generatePropertyFile("ammonite/ammonite-spark.properties"),
    generateDependenciesFile,
    testSettings,
    libraryDependencies ++= Seq(
      Deps.ammoniteRepl,
      Deps.utest
    )
  )

lazy val `standalone-tests` = project
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    dontPublish,
    testSettings
  )

lazy val `yarn-tests` = project
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    dontPublish,
    testSettings
  )

lazy val `yarn-spark-distrib-tests` = project
  .dependsOn(tests)
  .underModules
  .settings(
    shared,
    dontPublish,
    testSettings
  )

lazy val `ammonite-spark` = project
  .in(file("."))
  .aggregate(
    core,
    `spark-stubs_20`,
    `spark-stubs_24`,
    tests
  )
  .settings(
    shared,
    dontPublish
  )
