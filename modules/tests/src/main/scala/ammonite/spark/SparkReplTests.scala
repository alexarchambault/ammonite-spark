package ammonite.spark

import ammonite.spark.fromammonite.TestRepl
import utest._

class SparkReplTests(sparkVersion: String, master: String, conf: (String, String)*) extends TestSuite {

  // Most of the tests here were adapted from https://github.com/apache/spark/blob/ab18b02e66fd04bc8f1a4fb7b6a7f2773902a494/repl/src/test/scala/org/apache/spark/repl/SingletonReplSuite.scala

  Init.setupLog4j()

  val check = new TestRepl

  check.session(Init.init(master, sparkVersion, conf))

  override def utestAfterAll() =
    check.session(Init.end)

  def sparkSession(code: String): Unit =
    check.session(code)

  def inputUrlOpt: Option[String] =
    Some(
      Thread.currentThread()
        .getContextClassLoader
        .getResource("input.txt")
        .toURI
        .toASCIIString
    )

  val tests = Tests {

    // Beware that indentation of the session snippets is super sensitive.
    // All snippets should have the exact same indentation.


    "simple foreach with accumulator" - {
      sparkSession(
        """
            @ val accum = sc.longAccumulator
            accum: org.apache.spark.util.LongAccumulator = LongAccumulator(id: 0, name: None, value: 0)

            @ sc.parallelize(1 to 10).foreach(x => accum.add(x))

            @ val res = accum.value
            res: java.lang.Long = 55L
         """
      )
    }

    "external vars" - {
      sparkSession(
        """
            @ var v = 7
            v: Int = 7

            @ val res1 = sc.parallelize(1 to 10).map(x => v).collect().reduceLeft(_+_)
            res1: Int = 70

            @ v = 10

            @ val res2 = sc.parallelize(1 to 10).map(x => v).collect().reduceLeft(_+_)
            res2: Int = 100
         """
      )
    }

    "external classes" - {
      sparkSession(
        """
            @ class C {
            @   def foo = 5
            @ }
            defined class C

            @ val res = sc.parallelize(1 to 10).map(x => (new C).foo).collect().reduceLeft(_+_)
            res: Int = 50
         """
      )
    }

    "external functions" - {
      sparkSession(
        """
            @ def double(x: Int) = x + x
            defined function double

            @ val res = sc.parallelize(1 to 10).map(x => double(x)).collect().reduceLeft(_+_)
            res: Int = 110
         """
      )
    }

    "external functions that access vars" - {
      sparkSession(
        """
            @ var v = 7
            v: Int = 7

            @ def getV() = v
            defined function getV

            @ val res1 = sc.parallelize(1 to 10).map(x => getV()).collect().reduceLeft(_+_)
            res1: Int = 70

            @ v = 10

            @ val res2 = sc.parallelize(1 to 10).map(x => getV()).collect().reduceLeft(_+_)
            res2: Int = 100
         """
      )
    }

    def hasBroadcastIssue =
      master == "local" || master.startsWith("local[")

    "broadcast vars" - {
      // This test doesn't behave the right way in local mode, even in the original spark repl
      // (https://github.com/apache/spark/blob/181261a81d592b93181135a8267570e0c9ab2243/repl/scala-2.11/src/test/scala/org/apache/spark/repl/ReplSuite.scala#L219),
      // because of the way broadcasts work in local mode it seems.
      // res2 should be Array(0, 0, 0, 0, 0) instead of Array(5, 0, 0, 0, 0).
      // It passes fine in SingletonReplSuite.scala in spark, because they run tests in local *cluster* mode.

      val expectedSecondRes =
        if (hasBroadcastIssue)
          "Array(5, 0, 0, 0, 0)"
        else
          "Array(0, 0, 0, 0, 0)"

      sparkSession(
        s"""
            @ var array = new Array[Int](5)
            array: Array[Int] = Array(0, 0, 0, 0, 0)

            @ val broadcastArray = sc.broadcast(array)

            @ val res1 = sc.parallelize(0 to 4).map(x => broadcastArray.value(x)).collect()
            res1: Array[Int] = Array(0, 0, 0, 0, 0)

            @ array(0) = 5

            @ val res2 = sc.parallelize(0 to 4).map(x => broadcastArray.value(x)).collect()
            res2: Array[Int] = $expectedSecondRes
         """
      )
    }

    "interacting with files" - {
      inputUrlOpt match {
        case None =>
          println("input file not available, not running test")
        case Some(inputUrl) =>
          sparkSession(
            s"""
            @ val file = sc.textFile("$inputUrl").cache()

            @ val res1 = file.count()
            res1: Long = 3L

            @ val res2 = file.count()
            res2: Long = 3L

            @ val res3 = file.count()
            res3: Long = 3L
            """
          )
      }
    }

    "SPARK-1199 two instances of same class don't type check" - {
      sparkSession(
        """
            @ case class Sum(exp: String, exp2: String)
            defined class Sum

            @ val a = Sum("A", "B")
            a: Sum = Sum("A", "B")

            @ def b(a: Sum): String = a match { case Sum(_, _) => "OK" }
            defined function b

            @ val isItOk = b(a)
            isItOk: String = "OK"
        """
      )
    }

    "SPARK-2452 compound statements" - {
      sparkSession(
        """
            @ val x = 4 ; def f() = x
            x: Int = 4
            defined function f

            @ val resFoo = f()
            resFoo: Int = 4
        """
      )
    }

    "SPARK-2576 importing implicits" - {
      // FIXME The addOuterScope should be automatically added. (Tweak CodeClassWrapper for that?)
      sparkSession(
        """
            @ import spark.implicits._
            import spark.implicits._

            @ org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this); case class TestCaseClass(value: Int)
            defined class TestCaseClass

            @ val res = sc.parallelize(1 to 10).map(x => TestCaseClass(x)).toDF().collect()
            res: Array[Row] = Array([1], [2], [3], [4], [5], [6], [7], [8], [9], [10])

            @ val foo = Seq(TestCaseClass(1)).toDS().collect()
            foo: Array[TestCaseClass] = Array(TestCaseClass(1))
        """
      )
    }

    "Datasets and encoders" - {
      sparkSession(
        """
            @ import spark.implicits._
            import spark.implicits._

            @ import org.apache.spark.sql.functions._
            import org.apache.spark.sql.functions._

            @ import org.apache.spark.sql.{Encoder, Encoders}
            import org.apache.spark.sql.{Encoder, Encoders}

            @ import org.apache.spark.sql.expressions.Aggregator
            import org.apache.spark.sql.expressions.Aggregator

            @ import org.apache.spark.sql.TypedColumn
            import org.apache.spark.sql.TypedColumn

            @ val simpleSum = new Aggregator[Int, Int, Int] {
            @   def zero: Int = 0
            @   def reduce(b: Int, a: Int) = b + a
            @   def merge(b1: Int, b2: Int) = b1 + b2
            @   def finish(b: Int) = b
            @   def bufferEncoder: Encoder[Int] = Encoders.scalaInt
            @   def outputEncoder: Encoder[Int] = Encoders.scalaInt
            @ }.toColumn

            @ val ds = Seq(1, 2, 3, 4).toDS()
            ds: Dataset[Int] = [value: int]

            @ val res = ds.select(simpleSum).collect()
            res: Array[Int] = Array(10)
        """
      )
    }

    "SPARK-2632 importing a method from non serializable class and not using it" - {
      sparkSession(
        """
            @ class TestClass() { def testMethod = 3; override def toString = "TestClass" }
            defined class TestClass

            @ val t = new TestClass
            t: TestClass = TestClass

            @ import t.testMethod
            import t.testMethod

            @ case class TestCaseClass(value: Int)
            defined class TestCaseClass

            @ val res = sc.parallelize(1 to 10).map(x => TestCaseClass(x)).collect()
            res: Array[TestCaseClass] = Array(
              TestCaseClass(1),
              TestCaseClass(2),
              TestCaseClass(3),
              TestCaseClass(4),
              TestCaseClass(5),
              TestCaseClass(6),
              TestCaseClass(7),
              TestCaseClass(8),
              TestCaseClass(9),
              TestCaseClass(10)
            )
        """
      )
    }

    "collecting objects of class defined in repl" - {
      sparkSession(
        """
            @ case class Foo(i: Int)
            defined class Foo

            @ val res = sc.parallelize((1 to 100).map(Foo), 10).collect()
            res: Array[Foo] = Array(
              Foo(1),
              Foo(2),
              Foo(3),
              Foo(4),
              Foo(5),
              Foo(6),
              Foo(7),
              Foo(8),
              Foo(9),
              Foo(10),
            ...
        """
      )
    }

    "collecting objects of class defined in repl - shuffling" - {
      sparkSession(
        """
            @ case class Foo(i: Int)
            defined class Foo

            @ val list = List((1, Foo(1)), (1, Foo(2)))
            list: List[(Int, Foo)] = List((1, Foo(1)), (1, Foo(2)))

            @ val res = sc.parallelize(list).groupByKey().collect()
            res: Array[(Int, Iterable[Foo])] = Array((1, CompactBuffer(Foo(1), Foo(2))))
        """
      )
    }

    "replicating blocks of object with class defined in repl" - {

      // FIXME The actual test also does https://github.com/apache/spark/blob/ab18b02e66fd04bc8f1a4fb7b6a7f2773902a494/repl/src/test/scala/org/apache/spark/repl/SingletonReplSuite.scala#L353-L359

      sparkSession(
        """
            @ val timeout = 60000L
            timeout: Long = 60000L

            @ val start = System.currentTimeMillis

            @ import org.apache.spark.storage.StorageLevel._
            import org.apache.spark.storage.StorageLevel._

            @ case class Foo(i: Int)
            defined class Foo

            @ val ret = sc.parallelize((1 to 100).map(Foo), 10).persist(MEMORY_AND_DISK_2)

            @ val count = ret.count()
            count: Long = 100L

            @ val res = sc.getRDDStorageInfo.filter(_.id == ret.id).map(_.numCachedPartitions).sum
            res: Int = 10
        """
      )
    }

    "should clone and clean line object in ClosureCleaner" - {
      inputUrlOpt match {
        case None =>
          println("input file not available, not running test")
        case Some(inputUrl) =>
          sparkSession(
            s"""
            @ import org.apache.spark.rdd.RDD
            import org.apache.spark.rdd.RDD

            @ val lines = sc.textFile("$inputUrl")

            @ case class Data(s: String)
            defined class Data

            @ val dataRDD = lines.map(line => Data(line.take(3)))

            @ val count = dataRDD.cache.count()
            count: Long = 3L

            @ val repartitioned = dataRDD.repartition(dataRDD.partitions.size)

            @ val repartitionedCount = repartitioned.cache.count
            repartitionedCount: Long = 3L

            @ def getCacheSize(rdd: RDD[_]) = sc.getRDDStorageInfo.filter(_.id == rdd.id).map(_.memSize).sum
            defined function getCacheSize

            @ val cacheSize1 = getCacheSize(dataRDD)

            @ val cacheSize2 = getCacheSize(repartitioned)

            @ val deviation = math.abs(cacheSize2 - cacheSize1).toDouble / cacheSize1

            @ val res = deviation < 0.2
            res: Boolean = true
            """
          )
      }
    }

    "newProductSeqEncoder with REPL defined class" - {
      sparkSession(
        """
            @ case class Click(id: Int)
            defined class Click

            @ val res = spark.implicits.newProductSeqEncoder[Click] != null
            res: Boolean = true
        """
      )
    }


    // Adapted from https://github.com/apache/spark/blob/3d5c61e5fd24f07302e39b5d61294da79aa0c2f9/repl/src/test/scala/org/apache/spark/repl/ReplSuite.scala#L193-L208
    "line wrapper only initialized once when used as encoder outer scope" - {
      sparkSession(
        """
            @ val fileName = "repl-test-" + java.util.UUID.randomUUID()

            @ val tmpDir = System.getProperty("java.io.tmpdir")

            @ val file = new java.io.File(tmpDir, fileName)

            @ def createFile(): Unit = file.createNewFile()
            defined function createFile

            @ createFile(); case class TestCaseClass(value: Int)
            defined class TestCaseClass

            @ val exists = file.exists()
            exists: Boolean = true

            @ file.delete()

            @ val exists1 = file.exists()
            exists1: Boolean = false

            @ val res = sc.parallelize(1 to 10).map(x => TestCaseClass(x)).collect()
            res: Array[TestCaseClass] = Array(
              TestCaseClass(1),
              TestCaseClass(2),
              TestCaseClass(3),
              TestCaseClass(4),
              TestCaseClass(5),
              TestCaseClass(6),
              TestCaseClass(7),
              TestCaseClass(8),
              TestCaseClass(9),
              TestCaseClass(10)
            )

            @ val exists2 = file.exists()
            exists2: Boolean = false
        """
      )
    }

    // Adapted from https://github.com/apache/spark/blob/3d5c61e5fd24f07302e39b5d61294da79aa0c2f9/repl/src/test/scala/org/apache/spark/repl/ReplSuite.scala#L230-L238
    "spark-shell should find imported types in class constructors and extends clause" - {
      sparkSession(
        """
            @ import org.apache.spark.Partition
            import org.apache.spark.Partition

            @ class P(p: Partition)
            defined class P

            @ class P(val index: Int) extends Partition
            defined class P
        """
      )
    }

    // https://github.com/apache/spark/blob/3d5c61e5fd24f07302e39b5d61294da79aa0c2f9/repl/src/test/scala/org/apache/spark/repl/ReplSuite.scala#L240-L259
    "spark-shell should shadow val/def definitions correctly" - {
      sparkSession(
        """
            @ def myMethod() = "first definition"
            defined function myMethod

            @ val tmp = myMethod(); val out = tmp
            tmp: String = "first definition"
            out: String = "first definition"

            @ def myMethod() = "second definition"
            defined function myMethod

            @ val tmp = myMethod(); val out = s"$tmp aabbcc"
            tmp: String = "second definition"
            out: String = "second definition aabbcc"

            @ val a = 1
            a: Int = 1

            @ val b = a; val c = b
            b: Int = 1
            c: Int = 1

            @ val a = 2
            a: Int = 2

            @ val b = a; val c = b
            b: Int = 2
            c: Int = 2

            @ val res = s"!!$b!!"
            res: String = "!!2!!"
        """
      )
    }

    // tests below are custom ones

    "algebird" - {
      if (scala.util.Properties.versionNumberString.startsWith("2.11."))
        // no algebird-spark in scala 2.12 yet
        sparkSession(
          """
              @ import $ivy.`com.twitter::algebird-spark:0.13.0`

              @ AmmoniteSparkSession.sync()

              @ import com.twitter.algebird.Semigroup
              import com.twitter.algebird.Semigroup

              @ import com.twitter.algebird.spark._
              import com.twitter.algebird.spark._

              @ case class Foo(n: Int, weight: Double)
              defined class Foo

              @ implicit val fooSemigroup: Semigroup[Foo] = new Semigroup[Foo] {
              @   def plus(a: Foo, b: Foo): Foo =
              @     Foo(a.n + b.n, a.weight + b.weight)
              @ }

              @ val rdd = sc.parallelize((1 to 100).map(n => n.toString.take(1) -> Foo(n, n % 10)), 10)

              @ val res = rdd.algebird.sumByKey[String, Foo].collect().sortBy(_._1)
              res: Array[(String, Foo)] = Array(
                ("1", Foo(246, 46.0)),
                ("2", Foo(247, 47.0)),
                ("3", Foo(348, 48.0)),
                ("4", Foo(449, 49.0)),
                ("5", Foo(550, 50.0)),
                ("6", Foo(651, 51.0)),
                ("7", Foo(752, 52.0)),
                ("8", Foo(853, 53.0)),
                ("9", Foo(954, 54.0))
              )
          """
        )
    }

  }

}
