
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  private def ammoniteVersion = "2.3.8-122-9be39deb"
  def ammoniteCompiler = ("com.lihaoyi" % "ammonite-compiler" % ammoniteVersion).cross(CrossVersion.full)
  def ammoniteReplApi = ("com.lihaoyi" % "ammonite-repl-api" % ammoniteVersion).cross(CrossVersion.full)
  def ammoniteRepl = ("com.lihaoyi" % "ammonite-repl" % ammoniteVersion).cross(CrossVersion.full)

  def jettyServer = "org.eclipse.jetty" % "jetty-server" % "9.4.41.v20210516"
  def utest = "com.lihaoyi" %% "utest" % "0.7.10"

  def sparkSql = "org.apache.spark" %% "spark-sql" % "2.4.0"
  def sparkSql3 = "org.apache.spark" %% "spark-sql" % "3.0.0"

}
