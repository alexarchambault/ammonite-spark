package ammonite.spark

object Local24Tests extends SparkReplTests(
  SparkVersions.latest24,
  Local.master
)
