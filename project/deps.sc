import mill.scalalib._

object Versions {
  def scala212 = "2.12.11"
  def scala213 = "2.13.11"

  def scala = Seq(scala213, scala212)

  def almond        = "0.14.0-RC9"
  def ammonite      = "3.0.0-M0-44-1f535bd1"
  def jsoniterScala = "2.13.5"
}

object Deps {
  def almondToreeHooks = ivy"sh.almond::toree-hooks:${Versions.almond}"
  def ammoniteCompiler = ivy"sh.almond.tmp.ammonite:::ammonite-compiler:${Versions.ammonite}"
  def ammoniteReplApi  = ivy"sh.almond.tmp.ammonite:::ammonite-repl-api:${Versions.ammonite}"
  def ammoniteRepl     = ivy"sh.almond.tmp.ammonite:::ammonite-repl:${Versions.ammonite}"

  def classPathUtil = ivy"io.get-coursier::class-path-util:0.1.4"
  def jettyServer   = ivy"org.eclipse.jetty:jetty-server:9.4.51.v20230217"
  def jsoniterScalaCore =
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:${Versions.jsoniterScala}"
  def jsoniterScalaMacros =
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:${Versions.jsoniterScala}"
  def log4j2         = ivy"org.apache.logging.log4j:log4j-core:2.17.2"
  def scalaKernelApi = ivy"sh.almond:::scala-kernel-api:${Versions.almond}"
  def scalatags      = ivy"com.lihaoyi::scalatags:0.12.0"
  def sparkSql(sv: String) = {
    val ver =
      if (sv.startsWith("2.12.")) "2.4.0"
      else "3.2.0"
    ivy"org.apache.spark::spark-sql:$ver"
  }
  def sparkSql3  = ivy"org.apache.spark::spark-sql:3.0.0"
  def sparkSql32 = ivy"org.apache.spark::spark-sql:3.2.0"
  def utest      = ivy"com.lihaoyi::utest:0.8.1"
}
