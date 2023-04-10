package ammonite.spark

object Local32ScriptTests extends SparkReplTests(
      SparkVersions.latest32,
      Local.master
    ) {
  override def initFromPredef = true
}
