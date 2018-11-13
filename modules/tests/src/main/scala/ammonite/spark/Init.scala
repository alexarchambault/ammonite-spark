package ammonite.spark

import ammonite.spark.Properties.version

object Init {

  private def q = "\""

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
            @ ${optionalSparkSqlImport.fold("")(_ + "; ")}import $$ivy.`sh.almond::ammonite-spark:$version`

            @ import org.apache.spark.sql._

            @ assert(org.apache.spark.SPARK_VERSION == "$sparkVersion") // sanity check

            @ val spark = AmmoniteSparkSession.builder()${prependBuilderCalls.mkString}.appName("test-ammonite-spark").master("$master")${conf.map(t => s".config($q${t._1}$q, $q${t._2}$q)").mkString}.getOrCreate()

            @ def sc = spark.sparkContext"""
  }

  def end = "@ spark.sparkContext.stop()"

  def setupLog4j(): Unit =
    sys.props("log4j.configuration") = Thread.currentThread()
      .getContextClassLoader
      .getResource("log4j.properties")
      .toURI
      .toASCIIString


}
