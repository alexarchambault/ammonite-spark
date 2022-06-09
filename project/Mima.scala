
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaPlugin
import sbt._
import sbt.Keys._

import scala.sys.process._

object Mima {

  def binaryCompatibilityVersions: Set[String] =
    Seq("git", "tag", "--merged", "HEAD^", "--contains", "v0.9.0")
      .!!
      .linesIterator
      .map(_.trim)
      .filter(_.startsWith("v"))
      .map(_.stripPrefix("v"))
      .toSet

  def settings = Def.settings(
    MimaPlugin.autoImport.mimaPreviousArtifacts := {
      val sv = scalaVersion.value
      val binaryCompatibilityVersions0 =
        if (sv.startsWith("2.12.")) binaryCompatibilityVersions
        else
          binaryCompatibilityVersions.filter { v =>
            !v.startsWith("0.9.") &&
              !v.startsWith("0.10.") &&
              !v.startsWith("0.11.") &&
              !v.startsWith("0.12.")
          }
      binaryCompatibilityVersions0
        .map { ver =>
          (organization.value % moduleName.value % ver)
            .cross(crossVersion.value)
        }
    }
  )

}
