package ammsparkbuild

import mill.scalalib.*

object Deps {
  def almondToreeHooks = mvn"sh.almond::toree-hooks:${Versions.almond}"
  def ammoniteCompiler = mvn"sh.almond.tmp.ammonite:::ammonite-compiler:${Versions.ammonite}"
  def ammoniteReplApi  = mvn"sh.almond.tmp.ammonite:::ammonite-repl-api:${Versions.ammonite}"
  def ammoniteRepl     = mvn"sh.almond.tmp.ammonite:::ammonite-repl:${Versions.ammonite}"

  def classPathUtil = mvn"io.get-coursier::class-path-util:0.1.4"
  def jettyServer   = mvn"org.eclipse.jetty:jetty-server:9.4.58.v20250814"
  def jsoniterScalaCore =
    mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:${Versions.jsoniterScala}"
  def jsoniterScalaMacros =
    mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:${Versions.jsoniterScala}"
  def log4j2 = mvn"org.apache.logging.log4j:log4j-core:2.17.2"
  // slf4j -> log4j 1.x binding, matching the log4j.properties used by the tests.
  // Having it on the tests classpath ensures slf4j binds to log4j at startup,
  // rather than to the NOP logger - in particular for the spark-distrib tests,
  // where Spark (and its own slf4j binding) is only loaded later from SPARK_HOME.
  def slf4jLog4j12   = mvn"org.slf4j:slf4j-log4j12:1.7.36"
  def scalaKernelApi = mvn"sh.almond:::scala-kernel-api:${Versions.almond}"
  def scalatags      = mvn"com.lihaoyi::scalatags:0.13.1"

  // Spark-4 module variants, matching almond 0.14.5 exactly: upstream Ammonite 3.0.8 (com.lihaoyi,
  // not the sh.almond.tmp.ammonite fork) and almond 0.14.5, both on Scala 2.13.16.
  def ammoniteReplApiSpark4  = mvn"com.lihaoyi:::ammonite-repl-api:${Versions.ammoniteSpark4}"
  def ammoniteCompilerSpark4 = mvn"com.lihaoyi:::ammonite-compiler:${Versions.ammoniteSpark4}"
  def ammoniteReplSpark4     = mvn"com.lihaoyi:::ammonite-repl:${Versions.ammoniteSpark4}"
  def scalaKernelApiSpark4   = mvn"sh.almond:::scala-kernel-api:${Versions.almondSpark4}"
  def almondToreeHooksSpark4 = mvn"sh.almond::toree-hooks:${Versions.almondSpark4}"
  def sparkSql(sv: String) = {
    val ver =
      if (sv.startsWith("2.12.")) "2.4.0"
      else "3.2.0"
    mvn"org.apache.spark::spark-sql:$ver"
  }
  def sparkSql4 = mvn"org.apache.spark::spark-sql:${Versions.spark4}"
  def utest     = mvn"com.lihaoyi::utest:0.9.5"
}
