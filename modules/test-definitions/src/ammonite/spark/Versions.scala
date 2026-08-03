package ammonite.spark

object Versions {
  lazy val sparkVersion = Option(System.getenv("AMMSPARK_SPARK_VERSION")).getOrElse {
    sys.error("AMMSPARK_SPARK_VERSION not set")
  }
}
