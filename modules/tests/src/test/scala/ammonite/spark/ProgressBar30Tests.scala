package ammonite.spark

object ProgressBar30Tests extends ProgressBarTests(
  SparkVersions.latest30,
  Local.master
)
