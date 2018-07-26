package ammonite.spark

object ProgressBar22Tests extends ProgressBarTests(
  SparkVersions.latest22,
  Local.master
)
