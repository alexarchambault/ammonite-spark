package org.apache.spark.sql.almondinternals

import almond.interpreter.api.OutputHandler

import java.util.concurrent.ThreadFactory
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.DurationInt

final class StageElem(
  stageId: Int,
  numTasks: Int,
  keep: Boolean,
  name: String,
  details: String,
  useBars: Boolean
) {

  private val htmlName = {
    val sep = " at "
    val idx = name.indexOf(sep)
    if (idx < 0) s"<code>$name</code>"
    else s"<code>${name.take(idx)}</code> at <code>${name.drop(idx + sep.length)}</code>"
  }

  val displayId      = s"stage-info-${Id.generate()}"
  val titleDisplayId = s"$displayId-title"

  val startedTasks = new AtomicInteger
  val doneTasks    = new AtomicInteger

  @volatile var stageDone0 = false

  def taskStart(): Unit = {
    startedTasks.incrementAndGet()
  }

  def taskDone(): Unit = {
    doneTasks.incrementAndGet()
  }

  def stageDone(): Unit = {
    stageDone0 = true
  }

  val extraStyle = Seq(
    "word-wrap: normal",
    "white-space: nowrap",
    "text-align: center"
  )

  def init(cancelStageCommTargetName: String, sendInitCode: Boolean)(implicit
    publish: OutputHandler
  ): Unit =
    if (useBars)
      publish.html(
        s"""<div class="progress">
           |  <div class="progress-bar bg-success" role="progressbar" style="width: 0%; ${extraStyle.mkString(
            "; "
          )}; color: white" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
           |    0 / $numTasks
           |  </div>
           |</div>
           |""".stripMargin,
        id = displayId
      )
    else
      publish.html(
        s"<div>$htmlName</div>",
        id = displayId
      )

  def update()(implicit publish: OutputHandler): Unit = {

    val doneTasks0    = doneTasks.get()
    val startedTasks0 = startedTasks.get()
    val onGoingCount  = startedTasks0 - doneTasks0

    val donePct = math.round(100.0 * doneTasks0.toDouble / numTasks).toInt

    if (useBars) {
      val diff       = startedTasks0 - doneTasks0
      val startedPct = math.round(100.0 * (startedTasks0 - doneTasks0).toDouble / numTasks).toInt

      publish.updateHtml(
        s"""<div class="progress">
           |  <div class="progress-bar" role="progressbar" style="background-color: blue; width: $donePct%; ${extraStyle.mkString(
            "; "
          )}; color: white" aria-valuenow="$donePct" aria-valuemin="0" aria-valuemax="100">
           |    $doneTasks0${
            if (diff == 0) "" else s" + $diff"
          } / $numTasks
           |  </div>
           |  <div class="progress-bar" role="progressbar" style="background-color: red; width: $startedPct%" aria-valuenow="$startedPct" aria-valuemin="0" aria-valuemax="100"></div>
           |</div>
           |""".stripMargin,
        id = displayId
      )
    }
    else {
      val taskOrTasks    = if (numTasks <= 1) "task" else "tasks"
      val onGoingMessage =
        if (onGoingCount <= 0) ""
        else s", $onGoingCount on-going"
      val message =
        if (stageDone0) s"<div>$htmlName (done)</div>"
        else s"<div>$htmlName ($donePct% of $numTasks $taskOrTasks$onGoingMessage)</div>"
      publish.updateHtml(message, id = displayId)
    }

    if (stageDone0 && !keep) {
      // Allow the user to see the completed bar before wiping it
      val delay              = 3.seconds
      val runnable: Runnable =
        () =>
          try {
            publish.updateHtml("", id = displayId)
            if (useBars)
              publish.updateHtml("", id = titleDisplayId)
          }
          catch {
            case t: Throwable =>
              System.err.println("Error while updating message")
              t.printStackTrace(System.err)
          }
      StageElem.scheduler.schedule(runnable, delay.length, delay.unit)
    }
  }

}

object StageElem {
  private def keepAlive = 30.seconds
  lazy val scheduler    = {
    val executor = new ScheduledThreadPoolExecutor(
      1,
      new ThreadFactory {
        val count                                   = new AtomicInteger
        override def newThread(r: Runnable): Thread = {
          val name = s"almond-spark-progress-${count.getAndIncrement()}"
          val t    = new Thread(r, name)
          t.setDaemon(true)
          t
        }
      }
    )
    executor.setKeepAliveTime(keepAlive.length, keepAlive.unit)
    executor
  }

}
