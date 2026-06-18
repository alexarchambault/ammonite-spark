package ammonite.spark

object YarnSparkDistribTests extends SparkReplTests(
      sys.env("SPARK_VERSION"),
      "yarn",
      "spark.executor.instances"           -> "1",
      "spark.executor.memory"              -> "2g",
      "spark.yarn.executor.memoryOverhead" -> "1g",
      "spark.yarn.am.memory"               -> "2g"
    ) {

  if (!sys.env.contains("SPARK_HOME"))
    sys.error("SPARK_HOME not set")

  override def sparkHomeBased =
    true

  override def inputUrlOpt =
    Some(
      sys.env.getOrElse(
        "INPUT_TXT_URL",
        sys.error("INPUT_TXT_URL not set")
      )
    )
}
