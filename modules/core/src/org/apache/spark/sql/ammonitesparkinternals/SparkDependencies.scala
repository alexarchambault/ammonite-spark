package org.apache.spark.sql.ammonitesparkinternals

import java.net.URI

import coursierapi.{Dependency, Fetch, Module, Repository, ResolutionParams}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Properties.{versionNumberString => scalaVersion}
import scala.util.Try

object SparkDependencies {

  private val sbv =
    scalaVersion
      .split('.')
      .take(2)
      .mkString(".")

  private val sparkHiveClasses =
    List(
      // checking two classes just-in-case, first one is supposed to be deprecated
      "org.apache.spark.sql.hive.HiveContext",
      "org.apache.spark.sql.hive.HiveSessionStateBuilder"
    )

  private def sparkYarnClass = "org.apache.spark.deploy.yarn.Client"
  // Spark <= 3 ships this class in the spark-repl module (package org.apache.spark.repl); Spark 4
  // moved it into spark-core (package org.apache.spark.executor). Either one being already present
  // means we don't need to supply our own stub.
  private def sparkExecutorClassLoaderClasses =
    List(
      "org.apache.spark.executor.ExecutorClassLoader",
      "org.apache.spark.repl.ExecutorClassLoader"
    )

  def sparkHiveFound(): Boolean =
    sparkHiveClasses.exists { className =>
      try {
        Thread.currentThread().getContextClassLoader.loadClass(className)
        true
      }
      catch {
        case _: ClassNotFoundException =>
          false
      }
    }

  def sparkYarnFound(): Boolean =
    try {
      Thread.currentThread().getContextClassLoader.loadClass(sparkYarnClass)
      true
    }
    catch {
      case _: ClassNotFoundException =>
        false
    }

  def sparkExecutorClassLoaderFound(): Boolean =
    sparkExecutorClassLoaderClasses.exists { className =>
      try {
        Thread.currentThread().getContextClassLoader.loadClass(className)
        true
      }
      catch {
        case _: ClassNotFoundException =>
          false
      }
    }

  private def sparkModules(): Seq[String] = {

    val b = new mutable.ListBuffer[String]

    b ++= Seq("core", "sql")

    val cl = Thread.currentThread().getContextClassLoader

    @tailrec
    def addIfClasses(module: String, classNames: List[String]): Unit =
      classNames match {
        case Nil =>
        case h :: t =>
          try {
            cl.loadClass(h)
            b += module
          }
          catch {
            case _: ClassNotFoundException =>
              addIfClasses(module, t)
          }
      }

    def addIfClass(module: String, className: String): Unit =
      addIfClasses(module, className :: Nil)

    addIfClass("yarn", sparkYarnClass)
    addIfClasses("hive", sparkHiveClasses)
    addIfClass("mllib", "org.apache.spark.mllib.optimization")
    addIfClass("graphx", "org.apache.spark.graphx.Graph")
    addIfClass("streaming", "org.apache.spark.streaming.StreamingContext")

    b.result()
  }

  // Kept with its original signature, for binary compatibility with the previously published
  // versions of this artifact. Spark <= 3 - the only Spark this artifact supports - always has a
  // stub, so this never errors here; the Spark 4 artifact uses stubsDependencyOpt instead.
  def stubsDependency: Dependency =
    stubsDependencyOpt.getOrElse {
      sys.error(
        s"No spark-stubs artifact for Spark ${org.apache.spark.SPARK_VERSION} " +
          "(Spark >= 4 needs none - use stubsDependencyOpt)"
      )
    }

  // Returns None on Spark >= 4, which ships its own executor-side class loader and therefore needs
  // no stub - hence an Option rather than a plain Dependency.
  def stubsDependencyOpt: Option[Dependency] = {
    val sv = org.apache.spark.SPARK_VERSION
    val suffixOpt = sv.split('.').take(2) match {
      case Array("2", n) if Try(n.toInt).toOption.exists(_ <= 3) =>
        Some("20")
      case Array("2", n) if Try(n.toInt).toOption.exists(_ >= 4) =>
        Some("24")
      case Array("3", n) if Try(n.toInt).toOption.exists(_ <= 1) =>
        Some("30")
      case Array("3", _) =>
        Some("32")
      case Array(major, _) if Try(major.toInt).toOption.exists(_ >= 4) =>
        // Spark >= 4 ships its own executor-side class loader (org.apache.spark.executor.
        // ExecutorClassLoader in spark-core) and supports the spark.repl.class.outputDir
        // mechanism, so no ammonite-spark stub is needed.
        None
      case _ =>
        System.err.println(s"Warning: unrecognized Spark version ($sv), assuming 2.4.x")
        Some("24")
    }
    suffixOpt.map { suffix =>
      Dependency.of(
        "sh.almond",
        s"spark-stubs_${suffix}_$sbv",
        Properties.version
      )
    }
  }

  def sparkYarnDependency =
    Dependency.of(
      "org.apache.spark",
      s"spark-yarn_$sbv",
      org.apache.spark.SPARK_VERSION
    )

  def sparkHiveDependency =
    Dependency.of(
      "org.apache.spark",
      s"spark-hive_$sbv",
      org.apache.spark.SPARK_VERSION
    )

  private def sparkBaseDependencies() =
    Seq(
      Dependency.of("org.scala-lang", "scala-library", scalaVersion),
      Dependency.of("org.scala-lang", "scala-reflect", scalaVersion),
      Dependency.of("org.scala-lang", "scala-compiler", scalaVersion)
    ) ++
      stubsDependencyOpt.toSeq ++ // for ExecutorClassLoader (Spark <= 3 only)
      sparkModules().map { m =>
        Dependency.of("org.apache.spark", s"spark-${m}_$sbv", org.apache.spark.SPARK_VERSION)
      }

  def sparkJars(
    repositories: Seq[Repository],
    resolutionHooks: mutable.Buffer[Fetch => Fetch],
    profiles: Seq[String]
  ): Seq[URI] = {
    val fetch = Fetch.create()
      .addDependencies(sparkBaseDependencies(): _*)
      .withRepositories(repositories: _*)
      .withResolutionParams(
        ResolutionParams.create()
          .forceVersion(Module.of("org.scala-lang", "scala-library"), scalaVersion)
          .forceVersion(Module.of("org.scala-lang", "scala-reflect"), scalaVersion)
          .forceVersion(Module.of("org.scala-lang", "scala-compiler"), scalaVersion)
          .withProfiles(profiles.toSet.asJava)
      )

    resolutionHooks
      .foldLeft(fetch) { case (acc, f) => f(acc) }
      .fetch()
      .asScala
      .toVector
      .map(_.getAbsoluteFile.toURI)
  }
}
