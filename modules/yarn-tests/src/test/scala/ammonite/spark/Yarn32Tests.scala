package ammonite.spark

// Temporarily disabled, until we can (either or both)
// - enable the hadoop-2.7 profile (and excluding the remaining hadoop 3 dependencies?) when fetching Spark dependencies
// - update the docker-based hadoop setup in the tests to hadoop 3

// object Yarn32Tests extends SparkReplTests(
//   SparkVersions.latest32,
//   "yarn",
//   "spark.executor.instances" -> "1",
//   "spark.executor.memory" -> "2g",
//   "spark.yarn.executor.memoryOverhead" -> "1g",
//   "spark.yarn.am.memory" -> "2g"
// ) {
//   override def inputUrlOpt =
//     Some(
//       sys.env.getOrElse(
//         "INPUT_TXT_URL",
//         sys.error("INPUT_TXT_URL not set")
//       )
//     )
// }
