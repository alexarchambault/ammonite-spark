package almond.spark

object ToreeSql {

  var sqlLimit = 10

  private def sqlMagic(content: String): String = {
    val tq = "\"\"\""
    s"""_root_.almond.display.Html(_root_.almond.dfrenderer.AlmondDataFrameRenderer.render(spark.sql($tq$content$tq), limit = $sqlLimit))"""
  }

  def setup(): Unit = {
    almond.toree.CellMagicHook.addHandler("sql") { (_, content) =>
      Right(sqlMagic(content))
    }
    almond.toree.CellMagicHook.addHandler("sparksql") { (_, content) =>
      Right(sqlMagic(content))
    }
  }
}
