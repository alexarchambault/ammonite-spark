package org.apache.spark.sql.almondinternals

import java.lang.{Boolean => JBoolean}

import almond.api.JupyterApi
import almond.interpreter.api.{CommHandler, OutputHandler}
import almond.display.Display.html
import ammonite.interp.api.InterpAPI
import ammonite.repl.api.ReplAPI
import org.apache.spark.scheduler.{SparkListener, SparkListenerApplicationEnd}
// Spark 4 only ships log4j2 (no log4j 1.x), and its concrete session is the classic one.
import org.apache.spark.sql.classic.SparkSession
import org.apache.spark.sql.ammonitesparkinternals.AmmoniteSparkSessionBuilder

import scala.util.control.NonFatal

class NotebookSparkSessionBuilder(implicit
  interpApi: InterpAPI,
  replApi: ReplAPI,
  publish: OutputHandler,
  commHandler: CommHandler,
  jupyterApi: JupyterApi
) extends AmmoniteSparkSessionBuilder {

  override def toString = "NotebookSparkSessionBuilder"

  override def printLine(line: String, htmlLine: String = null): Unit =
    if (htmlLine == null)
      publish.html(line + System.lineSeparator())
    else
      publish.html(htmlLine)

  disableProgressBars(true)

  private var progress0 = true
  private var keep0     = false
  private var useBars0  = false

  private var logsInDeveloperConsoleOpt = Option.empty[Boolean]
  private var logsInKernelOutputOpt     = Option.empty[Boolean]

  private def defaultLogsInKernelOutput() =
    Option(System.getenv("ALMOND_SPARK_LOGS_IN_KERNEL_OUTPUT"))
      .exists(v => v == "1" || v == "true")

  def progress(
    enable: Boolean = true,
    keep: Boolean = false,
    useBars: Boolean = false
  ): this.type = {
    progress0 = enable
    keep0 = keep
    useBars0 = useBars
    this
  }

  def logsInDeveloperConsole(enable: JBoolean = null): this.type = {
    logsInDeveloperConsoleOpt = Option[JBoolean](enable).map[Boolean](x => x)
    this
  }

  def logsInKernelOutput(enable: JBoolean = null): this.type = {
    logsInKernelOutputOpt = Option[JBoolean](enable).map[Boolean](x => x)
    this
  }

  override def getOrCreate(): SparkSession = {

    // Spark 4 always uses log4j2.
    def defaultLogFileOpt = Log4j2File.logFile(classOf[SparkSession])

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

    var sendLogOpt          = Option.empty[SendLog]
    var sendLogToConsoleOpt = Option.empty[SendLogToConsole]

    try {
      sendLogOpt = logFileOpt.map { f =>
        SendLog.start(f)
      }

      if (logsInKernelOutputOpt.contains(true) && defaultLogFileOpt.isEmpty)
        Console.err.println(
          "Warning: cannot determine log file, logs won't be sent to the kernel console."
        )
      if (logsInKernelOutputOpt.getOrElse(defaultLogsInKernelOutput()))
        sendLogToConsoleOpt = defaultLogFileOpt.map { f =>
          SendLogToConsole.start(f)
        }

      val session = super.getOrCreate()

      val reverseProxyUrlOpt = session.sparkContext.getConf.getOption("spark.ui.reverseProxyUrl")
        .filter { _ =>
          session.sparkContext.getConf.getOption("spark.ui.reverseProxy").contains("true")
        }
      val uiUrlOpt = reverseProxyUrlOpt
        .map { reverseProxyUrl =>
          val appId = session.sparkContext.applicationId
          s"$reverseProxyUrl/proxy/$appId"
        }
        .orElse(session.sparkContext.uiWebUrl)
      for (url <- uiUrlOpt)
        html(s"""<a target="_blank" href="$url">Spark UI</a>""")

      session.sparkContext.addSparkListener(
        new ProgressSparkListener(session, keep0, progress0, useBars0)
      )

      session.sparkContext.addSparkListener(
        new SparkListener {
          override def onApplicationEnd(applicationEnd: SparkListenerApplicationEnd) = {
            sendLogOpt.foreach(_.stop())
            sendLogToConsoleOpt.foreach(_.stop())
          }
        }
      )

      session
    }
    catch {
      case NonFatal(e) =>
        sendLogOpt.foreach(_.stop())
        sendLogToConsoleOpt.foreach(_.stop())
        throw e
    }
  }

}
