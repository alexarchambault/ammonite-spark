package ammsparkbuild

import mill.*

final case class SparkMaven(
  sparkVersion: String,
  scalaVersion: String,
  jvmId: String
)

object SparkMaven {
  given Cross.ToSegments[SparkMaven] =
    new Cross.ToSegments(mvn =>
      List(
        Seq(mvn.sparkVersion, mvn.scalaVersion, mvn.jvmId)
          .map(_.replace('.', '_'))
          .mkString("__")
      )
    )
}
