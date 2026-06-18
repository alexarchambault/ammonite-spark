package ammonite.spark

object Local30Tests extends SparkReplTests(
      SparkVersions.latest30,
      Local.master
    )
