package ammonite.spark

object Local21Tests extends SparkReplTests(
  SparkVersions.latest21,
  Local.master
)
