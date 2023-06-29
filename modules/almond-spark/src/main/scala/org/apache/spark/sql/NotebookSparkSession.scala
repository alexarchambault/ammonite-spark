package org.apache.spark.sql

import almond.api.JupyterApi
import almond.interpreter.api.{CommHandler, OutputHandler}
import ammonite.repl.api.ReplAPI
import ammonite.interp.api.InterpAPI
import org.apache.spark.sql.almondinternals.NotebookSparkSessionBuilder

object NotebookSparkSession {

  def builder()(implicit
    interpApi: InterpAPI,
    replApi: ReplAPI,
    publish: OutputHandler,
    commHandler: CommHandler,
    jupyterApi: JupyterApi
  ): NotebookSparkSessionBuilder =
    new NotebookSparkSessionBuilder

  @deprecated(
    "Calling this method isn't needed any more, new dependencies are passed to Spark executors automatically",
    "0.14.0-RC1"
  )
  def sync(session: SparkSession = null)(implicit replApi: ReplAPI): SparkSession =
    AmmoniteSparkSession.sync(session)

}
