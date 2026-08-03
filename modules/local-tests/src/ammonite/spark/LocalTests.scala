package ammonite.spark

object LocalTests extends SparkReplTests(
      Versions.sparkVersion,
      Local.master
    )
