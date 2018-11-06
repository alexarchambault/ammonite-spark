package ammonite.spark

object Local22Tests extends SparkReplTests(
  SparkVersions.latest22,
  Local.master
)
