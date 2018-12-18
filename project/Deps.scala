
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  def ammoniteRepl = ("com.lihaoyi" % "ammonite-repl" % "1.5.0").cross(CrossVersion.full)
  def jettyServer = "org.eclipse.jetty" % "jetty-server" % "8.1.14.v20131031"
  def utest = "com.lihaoyi" %% "utest" % "0.6.4"

  def sparkSql20 = "org.apache.spark" %% "spark-sql" % "2.4.0" // no need to bump that version much, to ensure we don't rely on too new stuff
  def sparkSql24 = "org.apache.spark" %% "spark-sql" % "2.4.0" // that version's required for scala 2.12
  def sparkSql = setting {
    if (Settings.isAtLeast212.value)
      sparkSql24
    else
      sparkSql20
  }

}
