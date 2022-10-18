
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  object Scala {
    def scala212 = "2.12.11"
    def scala213 = "2.13.8"
  }

  private def ammoniteVersion = "2.5.5-11-626c15b0"
  def ammoniteCompiler = ("com.lihaoyi" % "ammonite-compiler" % ammoniteVersion).cross(CrossVersion.full)
  def ammoniteReplApi = ("com.lihaoyi" % "ammonite-repl-api" % ammoniteVersion).cross(CrossVersion.full)
  def ammoniteRepl = ("com.lihaoyi" % "ammonite-repl" % ammoniteVersion).cross(CrossVersion.full)

  def jettyServer = "org.eclipse.jetty" % "jetty-server" % "9.4.49.v20220914"
  def utest = "com.lihaoyi" %% "utest" % "0.8.1"

  def sparkSql = setting {
    val sv = scalaVersion.value
    val ver =
      if (sv.startsWith("2.12.")) "2.4.0"
      else "3.2.0"
    "org.apache.spark" %% "spark-sql" % ver
  }
  def sparkSql3 = "org.apache.spark" %% "spark-sql" % "3.0.0"
  def sparkSql32 = "org.apache.spark" %% "spark-sql" % "3.2.0"

}
