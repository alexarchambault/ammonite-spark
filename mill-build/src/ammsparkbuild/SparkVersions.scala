package ammsparkbuild

object SparkVersions {

  val sparkDistributions = Seq(
    SparkDistribution("2.4.2", hadoopVersion = "2.7", jvmId = "8"),
    // SparkDistribution("2.4.8", hadoopVersion = "2.7", jvmId = "8"), // uses Scala 2.11
    SparkDistribution("3.0.3", hadoopVersion = "2.7", jvmId = "8"),
    SparkDistribution("3.0.3", hadoopVersion = "3.2", jvmId = "8"),
    SparkDistribution("3.1.3", hadoopVersion = "2.7", jvmId = "8"),
    SparkDistribution("3.1.3", hadoopVersion = "3.2", jvmId = "8"),
    SparkDistribution("3.2.4", hadoopVersion = "2.7", jvmId = "8"),
    SparkDistribution("3.2.4", hadoopVersion = "3.2", jvmId = "8")
  )

  val sparkMavenVersions = Seq("2.4.4", "3.0.3", "3.2.4")

}
