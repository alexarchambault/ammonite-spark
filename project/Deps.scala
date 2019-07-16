
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  private val ammoniteVersion = setting {
    val sv = scalaVersion.value
    if (sv.startsWith("2.11."))
      "1.6.7"
    else
      "1.6.9-15-6720d42"
  }

  def ammoniteReplApi = setting {
    val sv = scalaVersion.value
    val mod =
      if (sv.startsWith("2.11."))
        "com.lihaoyi" % "ammonite-repl"
      else
        "com.lihaoyi" % "ammonite-repl-api"
    val ver = ammoniteVersion.value
    (mod % ver).cross(CrossVersion.full)
  }
  def ammoniteRepl = setting {
    val ver = ammoniteVersion.value
    ("com.lihaoyi" % "ammonite-repl" % ver).cross(CrossVersion.full)
  }
  def jettyServer = "org.eclipse.jetty" % "jetty-server" % "9.4.19.v20190610"
  def utest = "com.lihaoyi" %% "utest" % "0.6.7"

  def sparkSql20 = "org.apache.spark" %% "spark-sql" % "2.0.2" // no need to bump that version much, to ensure we don't rely on too new stuff
  def sparkSql24 = "org.apache.spark" %% "spark-sql" % "2.4.0" // that version's required for scala 2.12
  def sparkSql = setting {
    if (Settings.isAtLeast212.value)
      sparkSql24
    else
      sparkSql20
  }

}
