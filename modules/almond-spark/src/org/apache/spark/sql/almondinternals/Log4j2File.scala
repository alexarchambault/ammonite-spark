package org.apache.spark.sql.almondinternals

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.{Appender, Logger => Log4jLogger}
import org.apache.logging.log4j.core.appender.FileAppender

import java.io.File

import scala.collection.JavaConverters._

object Log4j2File {

  def logFile(clazz: Class[_]): Option[File] = {

    def appenders(log: Log4jLogger): Stream[Appender] =
      if (log == null)
        Stream()
      else
        log.getAppenders.asScala.values.toStream #::: appenders(log.getParent)

    appenders(LogManager.getLogger(clazz).asInstanceOf[Log4jLogger]).collectFirst {
      case fa: FileAppender => new File(fa.getFileName)
    }
  }

}
