package org.apache.spark.sql.ammonitesparkinternals

import java.net.URI

import coursier.util.Task
import coursier.{Cache, Dependency, Fetch, Module, Repository, Resolution}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext
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
    coursier.Dependency(
      coursier.Module("sh.almond", s"spark-stubs_${suffix}_$sbv"), Properties.version
    )
  }

  def sparkYarnDependency =
    coursier.Dependency(
      coursier.Module("org.apache.spark", s"spark-yarn_$sbv"), org.apache.spark.SPARK_VERSION
    )

  def sparkHiveDependency =
    coursier.Dependency(
      coursier.Module("org.apache.spark", s"spark-hive_$sbv"), org.apache.spark.SPARK_VERSION
    )

  private def sparkBaseDependencies() =
    Seq(
      Dependency(Module("org.scala-lang", "scala-library"), scalaVersion),
      Dependency(Module("org.scala-lang", "scala-reflect"), scalaVersion),
      Dependency(Module("org.scala-lang", "scala-compiler"), scalaVersion),
      stubsDependency // for ExecutorClassLoader
    ) ++
      sparkModules().map { m =>
        Dependency(Module("org.apache.spark", s"spark-${m}_$sbv"), org.apache.spark.SPARK_VERSION)
      }


  def sparkJars(
    repositories: Seq[Repository],
    profiles: Seq[String]
  ): Seq[URI] = {

    val start = Resolution(
      sparkBaseDependencies().toSet,
      forceVersions = Map(
        Module("org.scala-lang", "scala-library") -> scalaVersion,
        Module("org.scala-lang", "scala-reflect") -> scalaVersion,
        Module("org.scala-lang", "scala-compiler") -> scalaVersion
      ),
      userActivations =
        if (profiles.isEmpty) None
        else Some(profiles.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap)
    )

    val fetch = Fetch.from(repositories, Cache.fetch[Task]())

    val resolution = start.process.run(fetch).unsafeRun()(ExecutionContext.global)

    val errors = resolution.errors

    if (errors.nonEmpty) {
      for (((mod, ver), msg) <- errors) {
        Console.err.println(s"Error downloading $mod:$ver")
        for (m <- msg)
          Console.err.println("  " + m)
      }
      sys.error("Error while resolving spark dependencies")
    }

    val localArtifactsTasks = Task.gather.gather(
      resolution
        .dependencyArtifacts(withOptional = true)
        .map(_._2)
        .filter(a => a.`type` == "jar" || a.`type` == "bundle")
        .map(a => Cache.file[Task](a).run.map(e => (a.isOptional, e.left.map((a, _)))))
    )

    val localArtifactsRes = localArtifactsTasks.unsafeRun()(ExecutionContext.global)

    val fileErrors = localArtifactsRes.collect {
      case (false, Left(e)) => e
      case (true, Left(e @ (_, err))) if !err.notFound => e
    }

    if (fileErrors.nonEmpty) {
      for ((a, err) <- fileErrors)
        Console.err.println(s"Error downloading ${a.url}: ${err.describe}")
      sys.error("Error while downloading dependencies")
    }

    localArtifactsRes
      .flatMap(_._2.right.toOption)
      .map(_.getAbsoluteFile.toURI)
  }

}
