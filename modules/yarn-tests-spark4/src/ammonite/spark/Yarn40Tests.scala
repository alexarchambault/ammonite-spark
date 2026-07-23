package ammonite.spark

import utest._

// Temporarily disabled, like the Spark 3.2 YARN cross values, until the dockerized YARN test cluster
// is updated from hadoop-2.7 to hadoop 3 (Spark >= 3.2, and hence Spark 4, require hadoop 3). Once
// that is done, delete this empty stub and uncomment the real SparkReplTests below.

object Yarn40Tests extends TestSuite {
  def tests = Tests {
    test("empty") {}
  }
}

// object Yarn40Tests extends SparkReplTests(
//   Versions.sparkVersion,
//   "yarn",
//   "spark.executor.instances" -> "1",
//   "spark.executor.memory" -> "2g",
//   "spark.yarn.executor.memoryOverhead" -> "1g",
//   "spark.yarn.am.memory" -> "2g"
// ) {
//   override def ammoniteSparkArtifact = "ammonite-spark-spark4"
//   override def inputUrlOpt =
//     Some(
//       sys.env.getOrElse(
//         "INPUT_TXT_URL",
//         sys.error("INPUT_TXT_URL not set")
//       )
//     )
// }
