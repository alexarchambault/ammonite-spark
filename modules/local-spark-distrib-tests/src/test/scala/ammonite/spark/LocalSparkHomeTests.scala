package ammonite.spark

object LocalSparkHomeTests extends SparkReplTests(
      sys.env("SPARK_VERSION"),
      Local.master
    ) {
  override def sparkHomeBased =
    true
}
