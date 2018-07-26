package ammonite.spark

object Yarn23Tests extends SparkReplTests(
  "2.3.1",
  "yarn",
  "spark.executor.instances" -> "1",
  "spark.executor.memory" -> "2g",
  "spark.yarn.executor.memoryOverhead" -> "1g",
  "spark.yarn.am.memory" -> "2g"
) {
  override def inputUrlOpt =
    Some(
      sys.env.getOrElse(
        "INPUT_TXT_URL",
        sys.error("INPUT_TXT_URL not set")
      )
    )
}
