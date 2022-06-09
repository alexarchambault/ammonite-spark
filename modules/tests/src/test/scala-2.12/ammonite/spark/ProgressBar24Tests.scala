package ammonite.spark

object ProgressBar24Tests extends ProgressBarTests(
  SparkVersions.latest24,
  Local.master
)
