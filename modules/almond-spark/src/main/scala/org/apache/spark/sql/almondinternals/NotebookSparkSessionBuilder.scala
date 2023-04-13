package org.apache.spark.sql.almondinternals

import java.io.File
import java.lang.{Boolean => JBoolean}

import almond.interpreter.api.{CommHandler, OutputHandler}
import almond.display.Display.html
import ammonite.interp.api.InterpAPI
import ammonite.repl.api.ReplAPI
import org.apache.log4j.{Category, Logger, RollingFileAppender}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.ammonitesparkinternals.AmmoniteSparkSessionBuilder

import scala.collection.JavaConverters._

class NotebookSparkSessionBuilder(implicit
  interpApi: InterpAPI,
  replApi: ReplAPI,
  publish: OutputHandler,
  commHandler: CommHandler
) extends AmmoniteSparkSessionBuilder {

  private var progress0 = true
  private var keep0     = true

  private var logsInDeveloperConsoleOpt = Option.empty[Boolean]

  def progress(enable: Boolean = true, keep: Boolean = true): this.type = {
    progress0 = enable
    keep0 = keep
    this
  }

  def logsInDeveloperConsole(enable: JBoolean = null): this.type = {
    logsInDeveloperConsoleOpt = Option[JBoolean](enable).map[Boolean](x => x)
    this
  }

  override def getOrCreate(): SparkSession = {

    def defaultLogFileOpt = {
      val ver = org.apache.spark.SPARK_VERSION
      if (ver.startsWith("1.") || ver.startsWith("2."))
        Log4jFile.logFile(classOf[SparkSession])
      else
        Log4j2File.logFile(classOf[SparkSession])
    }

    val logFileOpt = logsInDeveloperConsoleOpt match {
      case Some(false) =>
        None
      case Some(true) =>
        val fileOpt = defaultLogFileOpt
        if (fileOpt.isEmpty)
          Console.err.println(
            "Warning: cannot determine log file, logs won't be sent to developer console."
          )
        fileOpt
      case None =>
        defaultLogFileOpt
    }

    var sendLogOpt = Option.empty[SendLog]

    try {
      sendLogOpt = logFileOpt.map { f =>
        println("See your browser developer console for detailed spark logs.")
        SendLog.start(f)
      }

      val session = super.getOrCreate()

      for (url <- session.sparkContext.uiWebUrl)
        html(s"""<a target="_blank" href="$url">Spark UI</a>""")

      session.sparkContext.addSparkListener(
        new ProgressSparkListener(session, keep0, progress0)
      )

      session
    }
    finally
      sendLogOpt.foreach(_.stop())
  }

}
