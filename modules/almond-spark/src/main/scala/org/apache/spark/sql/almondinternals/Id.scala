package org.apache.spark.sql.almondinternals

import java.util.concurrent.atomic.AtomicInteger
import java.util.{Locale, UUID}

object Id {

  def generate(): String =
    if (useRandomIds)
      UUID.randomUUID().toString
    else
      idCount.incrementAndGet().toString

  private lazy val useRandomIds: Boolean =
    Option(System.getenv("ALMOND_USE_RANDOM_IDS"))
      .orElse(sys.props.get("almond.ids.random"))
      .forall(s => s == "1" || s.toLowerCase(Locale.ROOT) == "true")

  private val idCount = new AtomicInteger(222222)

}
