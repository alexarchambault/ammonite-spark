package org.apache.spark.sql

import ammonite.repl.ReplAPI
import ammonite.interp.InterpAPI
import org.apache.spark.sql.ammonitesparkinternals.AmmoniteSparkSessionBuilder

object AmmoniteSparkSession {

  /**
    * In an Ammonite session, use this instead of [[SparkSession.builder()]].
    */
  def builder()
   (implicit
     interpApi: InterpAPI,
     replApi: ReplAPI
   ): AmmoniteSparkSessionBuilder =
    new AmmoniteSparkSessionBuilder

  /**
    * Should be called if new dependencies were added to the session *after* the [[SparkSession]] was
    * created.
    *
    * Example
    * {{{
    *
    *   @ import $ivy.`org.apache.spark::spark-sql:2.2.1`
    *   @ import $ivy.`sh.almond::ammonite-spark:0.1.0-SNAPSHOT`
    *   @ import org.apache.spark.sql._
    *
    *   @ val spark = AmmoniteSparkSession.builder().appName("test-ammonite").getOrCreate()
    *
    *   @ import $ivy.`com.twitter::algebird-spark:0.13.0`
    *
    *   @ AmmoniteSparkSession.sync()
    *
    * }}}
    *
    * @param session: [[SparkSession]] to add new JARs to
    */
  def sync(session: SparkSession = null)(implicit replApi: ReplAPI): SparkSession = {

    val session0 = Option(session).getOrElse {
      SparkSession.getDefaultSession.filter(!_.sparkContext.isStopped).getOrElse {
        sys.error("No active SparkSession found")
      }
    }

    val baseJars =
      session0.sparkContext.conf.get("spark.yarn.jars", "").split(',').filter(_.nonEmpty).toSet ++
        session0.sparkContext.conf.get("spark.jars", "").split(',').filter(_.nonEmpty)

    for {
      frame <- replApi.sess.frames
      f <- frame.classpath
      if AmmoniteSparkSessionBuilder.shouldPassToSpark(f)
      uri = f.toURI.toASCIIString
      if !baseJars(uri)
    }
      // addJar handles duplicates fine, no need to check for that
      session0.sparkContext.addJar(uri)

    session0
  }

}
