package ammonite.spark

object Local23Tests extends SparkReplTests(
  SparkVersions.latest23,
  Local.master
)
