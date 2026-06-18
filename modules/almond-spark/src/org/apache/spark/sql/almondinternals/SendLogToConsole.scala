package org.apache.spark.sql.almondinternals

import almond.api.JupyterApi

import java.io.{BufferedReader, File, FileReader, PrintStream}
import java.nio.file.{Files, StandardOpenOption}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class SendLogToConsole(
  f: File,
  out: PrintStream,
  threadName: String = "send-log-console",
  lineBufferSize: Int = 10,
  delay: FiniteDuration = 2.seconds
) {

  @volatile private var keepReading = true

  val thread: Thread =
    new Thread(threadName) {
      override def run(): Unit = {

        var r: FileReader = null

        try {
          // https://stackoverflow.com/questions/557844/java-io-implementation-of-unix-linux-tail-f/558262#558262

          r = new FileReader(f)
          val br = new BufferedReader(r)

          var lines = new ListBuffer[String]
          while (keepReading) {
            val line = br.readLine()
            if (line == null)
              // wait until there is more in the file
              Thread.sleep(delay.toMillis)
            else {
              lines += line

              while (keepReading && lines.length <= lineBufferSize && br.ready())
                lines += br.readLine()

              val l = lines.result()
              lines.clear()

              for (line <- l)
                out.println(line)
            }
          }
        }
        catch {
          case _: InterruptedException =>
          // normal exit
          case e: Throwable =>
            System.err.println(s"Thread $threadName crashed")
            e.printStackTrace(System.err)
        }
        finally
          if (r != null)
            r.close()
      }
    }

  def start(): Unit = {
    assert(keepReading, "Already stopped")
    if (!thread.isAlive)
      synchronized {
        if (!thread.isAlive)
          thread.start()
      }
  }

  def stop(): Unit = {
    keepReading = false
    thread.interrupt()
  }

}

object SendLogToConsole {

  def start(f: File)(implicit jupyterApi: JupyterApi): SendLogToConsole = {

    // It seems the file must exist for the reader above to get content appended to it.
    if (!f.exists())
      Files.write(f.toPath, Array.emptyByteArray, StandardOpenOption.CREATE)

    val sendLog = new SendLogToConsole(f, jupyterApi.consoleErr)
    sendLog.start()
    sendLog
  }

}
