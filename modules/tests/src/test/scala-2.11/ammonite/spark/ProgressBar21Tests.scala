package ammonite.spark

object ProgressBar21Tests extends ProgressBarTests(
  SparkVersions.latest21,
  Local.master
)
