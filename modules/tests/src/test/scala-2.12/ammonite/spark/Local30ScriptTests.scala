package ammonite.spark

object Local30ScriptTests extends SparkReplTests(
      SparkVersions.latest30,
      Local.master
    ) {
  override def initFromPredef = true
}
