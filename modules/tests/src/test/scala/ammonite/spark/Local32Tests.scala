package ammonite.spark

object Local32Tests extends SparkReplTests(
  SparkVersions.latest32,
  Local.master
)
