package almond.spark

import org.apache.spark.sql.{Dataset, Row}

object DataFrameRenderer {

  // inspired by https://github.com/apache/incubator-toree/blob/5b19aac2e56a56d35c888acc4ed5e549b1f4ed7c/kernel/src/main/scala/org/apache/toree/utils/DataFrameConverter.scala#L47-L59
  def render(df: Dataset[Row], limit: Int = 10): String = {
    import scalatags.Text.all._
    val columnFields = df.schema.fieldNames.toSeq.map(th(_))
    val columns      = tr(columnFields)
    val rows = df
      .rdd
      .map { row =>
        val fieldValues = row.toSeq.map(fieldToString).map(td(_))
        tr(fieldValues)
      }
      .take(limit)
    table(columns, rows).render
  }

  // https://github.com/apache/incubator-toree/blob/5b19aac2e56a56d35c888acc4ed5e549b1f4ed7c/kernel/src/main/scala/org/apache/toree/utils/DataFrameConverter.scala#L84-L89
  def fieldToString(any: Any): String =
    any match {
      case null        => "null"
      case seq: Seq[_] => seq.mkString("[", ", ", "]")
      case _           => any.toString
    }

}
