package org.apache.spark.sql.ammonitesparkinternals

object Properties {

  private lazy val props = {

    val p = new java.util.Properties

    try
      p.load(
        getClass
          .getClassLoader
          .getResourceAsStream(
            "org/apache/spark/sql/ammonitesparkinternals/ammonite-spark.properties"
          )
      )
    catch {
      case _: NullPointerException =>
    }

    p
  }

  lazy val version    = props.getProperty("version")
  lazy val commitHash = props.getProperty("commit-hash")

}
