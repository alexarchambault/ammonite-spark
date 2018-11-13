package ammonite.spark

object YarnSparkDistribTests extends SparkReplTests(
  sys.env("SPARK_VERSION"),
  "yarn",
  "spark.executor.instances" -> "1",
  "spark.executor.memory" -> "2g",
  "spark.yarn.executor.memoryOverhead" -> "1g",
  "spark.yarn.am.memory" -> "2g"
) {

  if (!sys.env.contains("SPARK_HOME"))
    sys.error("SPARK_HOME not set")

  override def init =
        s"""
            @ interp.load.cp {
            @   import java.nio.file.{Files, Paths},  scala.collection.JavaConverters._
            @   Files.list(Paths.get("/spark/jars"))
            @     .iterator()
            @     .asScala
            @     .toVector
            @     .filter(f => !f.getFileName.toString.startsWith("scala-compiler") && !f.getFileName.toString.startsWith("scala-reflect") && !f.getFileName.toString.startsWith("scala-library"))
            @     .sortBy(_.getFileName.toString)
            @     .map(ammonite.ops.Path(_))
            @ }
""" ++ Init.init(master, sparkVersion, conf, loadSparkSql = false)

  override def inputUrlOpt =
    Some(
      sys.env.getOrElse(
        "INPUT_TXT_URL",
        sys.error("INPUT_TXT_URL not set")
      )
    )
}
