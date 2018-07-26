
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import sbt._
import sbt.Keys._

object Settings {

  implicit class ProjectOps(val project: Project) extends AnyVal {
    def underModules: Project = {
      val base = project.base.getParentFile / "modules" / project.base.getName
      project.in(base)
    }
  }

  private val scala211 = "2.11.12"

  lazy val shared = Seq(
    organization := "sh.almond",
    scalaVersion := scala211,
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-explaintypes",
      "-encoding", "utf-8",
      "-unchecked"
    ),
    credentials ++= {

      val host = publishTo.value match {
        case Some(m: MavenRepository) =>
          new URL(m.root).getHost
        case Some(other) =>
          streams.value.log.warn(s"Unrecognized publishTo value: $other")
          "oss.sonatype.org"
        case None =>
          "oss.sonatype.org"
      }

      Seq("MAVEN_USER", "MAVEN_PASSWORD").map(sys.env.get) match {
        case Seq(Some(user), Some(pass)) =>
          Seq(Credentials("Sonatype Nexus Repository Manager", host, user, pass))
        case _ =>
          Seq()
      }
    }
  )

  lazy val testSettings = Seq(
    fork.in(Test) := true, // Java serialization goes awry without that
    testFrameworks += new TestFramework("utest.runner.Framework"),
    javaOptions.in(Test) ++= Seq("-Xmx3g", "-Dfoo=bzz")
  )

  lazy val dontPublish = Seq(
    publish := (),
    publishLocal := (),
    publishArtifact := false
  )

  def generatePropertyFile(path: String) =
    resourceGenerators.in(Compile) += Def.task {
      import sys.process._

      val dir = classDirectory.in(Compile).value
      val ver = version.value

      val f = path.split('/').foldLeft(dir)(_ / _)
      f.getParentFile.mkdirs()

      val p = new java.util.Properties

      p.setProperty("version", ver)
      p.setProperty("commit-hash", Seq("git", "rev-parse", "HEAD").!!.trim)

      val w = new java.io.FileOutputStream(f)
      p.store(w, "Almond properties")
      w.close()

      state.value.log.info(s"Wrote $f")

      Seq(f)
    }

  lazy val generateDependenciesFile =
    resourceGenerators.in(Compile) += Def.task {

      val dir = classDirectory.in(Compile).value / "ammonite" / "spark"
      val res = coursier.CoursierPlugin.autoImport.coursierResolutions
        .value
        .collectFirst {
          case (scopes, r) if scopes("compile") =>
            r
        }
        .getOrElse(
          sys.error("compile coursier resolution not found")
        )

      val content = res
        .minDependencies
        .toVector
        .map { d =>
          (d.module.organization, d.module.name, d.version)
        }
        .sorted
        .map {
          case (org, name, ver) =>
            s"$org:$name:$ver"
        }
        .mkString("\n")

      val f = dir / "amm-test-dependencies.txt"
      dir.mkdirs()

      Files.write(f.toPath, content.getBytes(UTF_8))

      state.value.log.info(s"Wrote $f")

      Seq(dir)
    }

}
