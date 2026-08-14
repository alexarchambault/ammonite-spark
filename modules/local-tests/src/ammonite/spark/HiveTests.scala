package ammonite.spark

import ammonite.spark.fromammonite.TestRepl
import utest._

object HiveTests extends TestSuite {

  Init.setupLog4j(Versions.sparkVersion)

  private val check       = new TestRepl
  private val hiveRoot    = os.temp.dir(prefix = "ammonite-spark-hive-tests")
  private val warehouse   = hiveRoot / "warehouse"
  private val metastoreDb = hiveRoot / "metastore"

  check.session(
    Init.init(
      Local.master,
      Versions.sparkVersion,
      Seq(
        "spark.sql.catalogImplementation" -> "hive",
        "spark.sql.warehouse.dir"         -> warehouse.toNIO.toUri.toASCIIString,
        "spark.hadoop.javax.jdo.option.ConnectionURL" ->
          s"jdbc:derby:;databaseName=${metastoreDb.toString};create=true"
      )
    )
  )

  override def utestAfterAll(): Unit = {
    check.session(Init.end)
    os.remove.all(hiveRoot)
  }

  val tests = Tests {
    test("enable Hive support") {
      val hiveQueries =
        if (Versions.sparkVersion.startsWith("2."))
          ""
        else
          """
            @ spark.sql("CREATE TABLE hive_test (value INT) USING hive")

            @ spark.sql("INSERT INTO hive_test VALUES (2), (1)")

            @ val values = spark.sql("SELECT value FROM hive_test ORDER BY value").collect().map(_.getInt(0))

            @ assert(values.sameElements(Array(1, 2)))

            @ spark.sql("DROP TABLE hive_test")
          """

      check.session(
        s"""
            @ val catalogImplementation = spark.conf.get("spark.sql.catalogImplementation")
            catalogImplementation: String = "hive"

            $hiveQueries
        """
      )
    }
  }
}
