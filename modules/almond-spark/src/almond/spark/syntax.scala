package almond.spark

import almond.display.Html
import org.apache.spark.sql._
import org.apache.spark.{SparkConf, SparkContext}

object syntax {
  implicit class KernelToreeSparkOps(private val kernel: almond.api.JupyterApi) {
    def sparkContext: SparkContext = sparkSession.sparkContext
    def sparkConf: SparkConf       = sparkContext.getConf
    def sparkSession: SparkSession = SparkSession.builder().getOrCreate()
  }
  implicit class AlmondNetflixDataFrameOps(private val df: DataFrame) {
    def render(limit: Int = 10): Html =
      Html(DataFrameRenderer.render(df, limit = limit))
  }
}
