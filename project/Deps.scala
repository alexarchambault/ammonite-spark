
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  private def ammoniteVersion = "1.6.9-18-a50caad"
  def ammoniteReplApi = ("com.lihaoyi" % "ammonite-repl-api" % ammoniteVersion).cross(CrossVersion.full)
  def ammoniteRepl = ("com.lihaoyi" % "ammonite-repl" % ammoniteVersion).cross(CrossVersion.full)

  def jettyServer = "org.eclipse.jetty" % "jetty-server" % "9.4.20.v20190813"
  def utest = "com.lihaoyi" %% "utest" % "0.7.1"

  def sparkSql = "org.apache.spark" %% "spark-sql" % "2.4.0"

}
