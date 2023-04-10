import mill.scalalib._

object Versions {
  def scala212 = "2.12.11"
  def scala213 = "2.13.10"

  def scala = Seq(scala213, scala212)

  def ammonite = "3.0.0-M0-14-c12b6a59"
}

object Deps {
  def ammoniteCompiler = ivy"com.lihaoyi:::ammonite-compiler:${Versions.ammonite}"
  def ammoniteReplApi = ivy"com.lihaoyi:::ammonite-repl-api:${Versions.ammonite}"
  def ammoniteRepl = ivy"com.lihaoyi:::ammonite-repl:${Versions.ammonite}"

  def jettyServer = ivy"org.eclipse.jetty:jetty-server:9.4.50.v20221201"
  def sparkSql(sv: String) = {
    val ver =
      if (sv.startsWith("2.12.")) "2.4.0"
      else "3.2.0"
    ivy"org.apache.spark::spark-sql:$ver"
  }
  def sparkSql3 = ivy"org.apache.spark::spark-sql:3.0.0"
  def sparkSql32 = ivy"org.apache.spark::spark-sql:3.2.0"
  def utest = ivy"com.lihaoyi::utest:0.8.1"
}
