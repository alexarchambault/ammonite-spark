
import sbt._

object Deps {

  def ammoniteRepl = ("com.lihaoyi" % "ammonite-repl" % "1.3.2").cross(CrossVersion.full)
  def jettyServer = "org.eclipse.jetty" % "jetty-server" % "8.1.14.v20131031"
  def sparkSql = "org.apache.spark" %% "spark-sql" % "2.0.2" // no need to bump that version much, to ensure we don't rely on too new stuff
  def utest = "com.lihaoyi" %% "utest" % "0.6.4"

}
