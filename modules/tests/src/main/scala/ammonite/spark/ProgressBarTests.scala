package ammonite.spark

import ammonite.spark.fromammonite.TestRepl
import utest._

class ProgressBarTests(sparkVersion: String, master: String, conf: (String, String)*) extends TestSuite {

  Init.setupLog4j()

  val check = new TestRepl

  val interactive = System.console() != null

  check.session(Init.init(master, sparkVersion, conf, Seq(".progressBars()")))

  override def utestAfterAll() =
    check.session(Init.end)

  def sparkSession(code: String): Unit =
    check.session(code)

  val tests = Tests {

    "dummy test" - {
      sparkSession(
        if (interactive)
          """
            @ val rdd = spark.sparkContext.makeRDD(1 to 10000, 200)

            @ val res = rdd.map(_ + 1).map(n => n.toString.take(1) -> n).groupByKey.collect().sortBy(_._1).map(t => (t._1, t._2.toVector.sorted))
            res: Array[(String, Vector[Int])] = Array(
              (
                "1",
                Vector(
                  10,
                  11,
                  12,
                  13,
                  14,
                  15,
                  16,
            ...
         """
        else
          """
            @ val rdd = spark.sparkContext.makeRDD(1 to 10000, 200)
         """
      )
    }

  }

}
