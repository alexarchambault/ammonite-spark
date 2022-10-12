package ammonite.spark

object StandaloneScriptTests extends SparkReplTests(
  sys.env("STANDALONE_SPARK_VERSION"),
  sys.env("STANDALONE_SPARK_MASTER"),
  "spark.executor.instances" -> "1",
  "spark.executor.memory" -> "2g"
) {
  override def initFromPredef = true
}
