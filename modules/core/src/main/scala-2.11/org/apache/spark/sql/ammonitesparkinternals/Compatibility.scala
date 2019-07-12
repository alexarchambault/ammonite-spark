package org.apache.spark.sql.ammonitesparkinternals

import ammonite.runtime.SpecialClassLoader

object Compatibility {

  implicit class SpecialLoaderOps(private val cl: SpecialClassLoader) {
    def inMemoryClasses: Map[String, Array[Byte]] =
      cl.newFileDict.toMap
  }

}
