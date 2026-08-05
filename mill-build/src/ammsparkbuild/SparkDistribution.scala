package ammsparkbuild

import mill.*

final case class SparkDistribution(
  sparkVersion: String,
  // scalaVersion: String,
  jvmId: String
)

object SparkDistribution {
  given Cross.ToSegments[SparkDistribution] =
    new Cross.ToSegments(distribution =>
      List(
        Seq(distribution.sparkVersion, distribution.jvmId)
          .map(_.replace('.', '_'))
          .mkString("__")
      )
    )
}
