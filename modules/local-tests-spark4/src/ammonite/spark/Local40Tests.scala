package ammonite.spark

object Local40Tests extends SparkReplTests(
      Versions.sparkVersion,
      Local.master
    ) {
  override def ammoniteSparkArtifact = "ammonite-spark-spark4"
}
