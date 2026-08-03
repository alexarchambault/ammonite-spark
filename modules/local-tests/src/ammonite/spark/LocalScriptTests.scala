package ammonite.spark

object LocalScriptTests extends SparkReplTests(
      Versions.sparkVersion,
      Local.master
    ) {
  override def initFromPredef = true
}
