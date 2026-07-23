package org.apache.spark.sql.almondinternals

import org.apache.log4j.{Category, Logger, RollingFileAppender}

import java.io.File

import scala.collection.JavaConverters._

object Log4jFile {

  def logFile(clazz: Class[_]): Option[File] = {

    def appenders(log: Category): Stream[Any] =
      if (log == null)
        Stream()
      else
        log.getAllAppenders.asScala.toStream #::: appenders(log.getParent)

    appenders(Logger.getLogger(clazz)).collectFirst {
      case rfa: RollingFileAppender => new File(rfa.getFile)
    }
  }

}
