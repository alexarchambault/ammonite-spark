package ammonite.spark

import ammonite.spark.Properties.version

object Init {

  private def q = "\""

  // Extra Spark config injected into every session. SPARK_DRIVER_HOST is set by
  // the dockerized YARN test runner so that the ApplicationMaster can reach the
  // driver back (see mill-in-docker-with-yarn-cluster.sh). Empty for the local
  // and standalone tests, where the variable isn't set.
  private def extraConf: Seq[(String, String)] =
    sys.env.get("SPARK_DRIVER_HOST").toSeq.map(h => "spark.driver.host" -> h)

  def init(
    master: String,
    sparkVersion: String,
    conf: Seq[(String, String)],
    prependBuilderCalls: Seq[String] = Nil,
    loadSparkSql: Boolean = true
  ): String = {

    val optionalSparkSqlImport =
      if (loadSparkSql)
        Some(s"import $$ivy.`org.apache.spark::spark-sql:$sparkVersion`")
      else
        None

        s"""
            @ ${optionalSparkSqlImport.fold("")(
            _ + "; "
          )}import $$ivy.`sh.almond::ammonite-spark:$version`

            @ import org.apache.spark.sql._

            @ assert(org.apache.spark.SPARK_VERSION == "$sparkVersion") // sanity check

            @ val spark = AmmoniteSparkSession.builder()${prependBuilderCalls.mkString}.appName("test-ammonite-spark").master("$master")${(conf ++ extraConf).map(
            t => s".config($q${t._1}$q, $q${t._2}$q)"
          ).mkString}.getOrCreate()

            @ def sc = spark.sparkContext"""
  }

  def scriptInit(
    master: String,
    sparkVersion: String,
    conf: Seq[(String, String)],
    prependBuilderCalls: Seq[String] = Nil,
    loadSparkSql: Boolean = true
  ): String = {

    val optionalSparkSqlImport =
      if (loadSparkSql)
        Some(s"import $$ivy.`org.apache.spark::spark-sql:$sparkVersion`")
      else
        None

    s"""${optionalSparkSqlImport.fold("")(_ + "; ")}
       |import $$ivy.`sh.almond::ammonite-spark:$version`
       |import org.apache.spark.sql._
       |
       |assert(org.apache.spark.SPARK_VERSION == "$sparkVersion") // sanity check
       |
       |val spark = AmmoniteSparkSession.builder()${prependBuilderCalls.mkString}.appName("test-ammonite-spark").master("$master")${(conf ++ extraConf).map(
        t => s".config($q${t._1}$q, $q${t._2}$q)"
      ).mkString}.getOrCreate()
       |def sc = spark.sparkContext
       |""".stripMargin
  }

  def sparkHomeInit(
    master: String,
    sparkVersion: String,
    conf: Seq[(String, String)],
    prependBuilderCalls: Seq[String] = Nil
  ): String =
    s"""
            @ interp.load.cp {
            @   import java.nio.file.{Files, Paths},  scala.collection.JavaConverters._
            @   Files.list(Paths.get(s"$${sys.env("SPARK_HOME")}/jars"))
            @     .iterator()
            @     .asScala
            @     .toVector
            @     .filter(f => !f.getFileName.toString.startsWith("scala-compiler") && !f.getFileName.toString.startsWith("scala-reflect") && !f.getFileName.toString.startsWith("scala-library") && !f.getFileName.toString.startsWith("spark-repl_"))
            @     .sortBy(_.getFileName.toString)
            @     .map(os.Path(_))
            @ }
""" ++ init(master, sparkVersion, conf, loadSparkSql = false)

  def end = "@ spark.sparkContext.stop()"

  def setupLog4j(): Unit = {
    // Set SPARK_LOG_CONSOLE to also get the Spark logs on the console (at INFO
    // level), which helps debugging the tests. By default, logs only go to the
    // spark.log file.
    val resource =
      if (sys.env.contains("SPARK_LOG_CONSOLE")) "log4j-console.properties"
      else "log4j.properties"
    sys.props("log4j.configuration") = Thread.currentThread()
      .getContextClassLoader
      .getResource(resource)
      .toURI
      .toASCIIString

    // Force slf4j to bind now (to the log4j 1.x backend on the tests classpath),
    // before the spark-distrib tests load Spark - and its own slf4j jars - from
    // SPARK_HOME. Otherwise slf4j would have already defaulted to the NOP logger,
    // silently dropping all the Spark logs.
    org.slf4j.LoggerFactory.getLogger("ammonite.spark").debug("slf4j logging initialized")
  }

}
