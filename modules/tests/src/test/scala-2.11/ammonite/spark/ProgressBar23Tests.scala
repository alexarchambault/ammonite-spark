package ammonite.spark

object ProgressBar23Tests extends ProgressBarTests(
  SparkVersions.latest23,
  Local.master
)
