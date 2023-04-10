package ammonite.spark

object ProgressBar32Tests extends ProgressBarTests(
      SparkVersions.latest32,
      Local.master
    )
