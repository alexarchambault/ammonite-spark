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
  private def sparkExecutorClassLoaderClass = "org.apache.spark.repl.ExecutorClassLoader"

  def sparkHiveFound(): Boolean =
    sparkHiveClasses.exists { className =>
      try {
        Thread.currentThread().getContextClassLoader.loadClass(className)
        true
      } catch {
        case _: ClassNotFoundException =>
          false
      }
    }

  def sparkYarnFound(): Boolean =
    try {
      Thread.currentThread().getContextClassLoader.loadClass(sparkYarnClass)
      true
    } catch {
      case _: ClassNotFoundException =>
        false
    }

  def sparkExecutorClassLoaderFound(): Boolean =
    try {
      Thread.currentThread().getContextClassLoader.loadClass(sparkExecutorClassLoaderClass)
      true
    } catch {
      case _: ClassNotFoundException =>
        false
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
          } catch {
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

  def stubsDependency = {
    val suffix = org.apache.spark.SPARK_VERSION.split('.').take(2) match {
      case Array("2", n) if Try(n.toInt).toOption.exists(_ <= 3) =>
        "20"
      case _ =>
        "24"
    }
    Dependency.of(
      "sh.almond", s"spark-stubs_${suffix}_$sbv", Properties.version
    )
  }

  def sparkYarnDependency =
    Dependency.of(
      "org.apache.spark", s"spark-yarn_$sbv", org.apache.spark.SPARK_VERSION
    )

  def sparkHiveDependency =
    Dependency.of(
      "org.apache.spark", s"spark-hive_$sbv", org.apache.spark.SPARK_VERSION
    )

  private def sparkBaseDependencies() =
    Seq(
      Dependency.of("org.scala-lang", "scala-library", scalaVersion),
      Dependency.of("org.scala-lang", "scala-reflect", scalaVersion),
      Dependency.of("org.scala-lang", "scala-compiler", scalaVersion),
      stubsDependency // for ExecutorClassLoader
    ) ++
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
      .foldLeft(fetch){ case (acc, f) => f(acc) }
      .fetch()
      .asScala
      .toVector
      .map(_.getAbsoluteFile.toURI)
  }
}
