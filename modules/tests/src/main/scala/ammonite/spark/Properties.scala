package ammonite.spark

import java.util.{Properties => JProperties}

object Properties {

  private val props = {

    val p = new JProperties

    try {
      p.load(
        getClass
          .getClassLoader
          .getResourceAsStream("ammonite/ammonite-spark.properties")
      )
    } catch  {
      case _: NullPointerException =>
    }

    p
  }

  val version = props.getProperty("version")

}
