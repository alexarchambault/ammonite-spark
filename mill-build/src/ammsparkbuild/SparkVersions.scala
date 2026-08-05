package ammsparkbuild

object SparkVersions {

  val sparkDistributions = Seq(
    SparkDistribution("2.4.2", jvmId = "8"),
    // SparkDistribution("2.4.8", jvmId = "8"), // uses Scala 2.11
    SparkDistribution("3.0.3", jvmId = "8"),
    SparkDistribution("3.1.3", jvmId = "8"),
    SparkDistribution("3.2.4", jvmId = "8")
  )

  val sparkMavenVersions = Seq("2.4.4", "3.0.0", "3.2.0")

}
