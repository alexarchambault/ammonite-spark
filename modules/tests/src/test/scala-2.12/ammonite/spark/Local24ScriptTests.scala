package ammonite.spark

object Local24ScriptTests extends SparkReplTests(
  SparkVersions.latest24,
  Local.master
) {
  override def initFromPredef = true
}
