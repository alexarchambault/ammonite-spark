package org.apache.spark.sql.ammonitesparkinternals

import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import ammonite.repl.ReplAPI
import ammonite.interp.InterpAPI
import org.apache.spark.SparkContext
import org.apache.spark.scheduler.{SparkListener, SparkListenerApplicationEnd}
import org.apache.spark.sql.SparkSession
import org.apache.spark.ui.ConsoleProgressBar

object AmmoniteSparkSessionBuilder {

  private def prettyDir(dir: String): String = {
    val home = sys.props("user.home")

    if (dir.startsWith(home))
      "~" + dir.stripPrefix(home)
    else
      dir
  }

  private def confEnvVars: Seq[String] = {

    val sparkBinaryVersion =
      org.apache.spark.SPARK_VERSION
        .split('.')
        .take(2)
        .mkString(".")

    Seq(
      "SPARK_CONF_DEFAULTS",
      s"SPARK_CONF_DEFAULTS_${sparkBinaryVersion.filter(_ != '.')}"
    )
  }

  def shouldPassToSpark(classpathEntry: File): Boolean =
    classpathEntry.isFile &&
      classpathEntry.getName.endsWith(".jar") &&
      !classpathEntry.getName.endsWith("-sources.jar")

  def classpath(cl: ClassLoader): Stream[java.net.URL] = {
    if (cl == null)
      Stream()
    else {
      val cp = cl match {
        case u: java.net.URLClassLoader => u.getURLs.toStream
        case _ => Stream()
      }

      cp #::: classpath(cl.getParent)
    }
  }

  def forceProgressBars(sc: SparkContext): Boolean =
    sc.progressBar.nonEmpty || {
      try {
        val method = classOf[org.apache.spark.SparkContext].getDeclaredMethod("org$apache$spark$SparkContext$$_progressBar_$eq", classOf[Option[Any]])
        method.setAccessible(true)
        method.invoke(sc, Some(new ConsoleProgressBar(sc)))
        true
      } catch {
        case _: NoSuchMethodException =>
          false
      }
    }

}

class AmmoniteSparkSessionBuilder
 (implicit
   interpApi: InterpAPI,
   replApi: ReplAPI
 ) extends SparkSession.Builder {

  private val options0: scala.collection.Map[String, String] =
    try {
      val f = classOf[SparkSession.Builder].getDeclaredField("org$apache$spark$sql$SparkSession$Builder$$options")
      f.setAccessible(true)
      f.get(this).asInstanceOf[scala.collection.mutable.HashMap[String, String]]
    } catch {
      case t: Throwable =>
        println(s"Warning: can't read SparkSession Builder options, caught $t")
        Map.empty[String, String]
    }

  private def init(): Unit = {

    for {
      envVar <- AmmoniteSparkSessionBuilder.confEnvVars
      path <- sys.env.get(envVar)
    } {
      println(s"Loading spark conf from ${AmmoniteSparkSessionBuilder.prettyDir(path)}")
      loadConf(path)
    }

    config("spark.submit.deployMode", "client")

    for (master0 <- sys.env.get("SPARK_MASTER"))
      master(master0)
  }

  init()

  def loadConf(path: String, ignore: Set[String] = Set()): this.type = {

    val content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)

    val extraProps = content
      .lines
      .toVector
      .filter(!_.startsWith("#"))
      .map(_.split("\\s+", 2))
      .collect { case Array(k, v) => k -> v }

    for ((k, v) <- extraProps if !ignore(k))
      config(k, v)

    this
  }

  private var forceProgressBars0 = false

  def progressBars(force: Boolean = true): this.type = {
    forceProgressBars0 = force
    this
  }

  var classServerOpt = Option.empty[AmmoniteClassServer]

  private def isYarn(): Boolean =
    options0.get("spark.master").exists(_.startsWith("yarn"))

  private def hiveSupport(): Boolean =
    options0.get("spark.sql.catalogImplementation").contains("hive")

  private def host(): String =
    options0.get("spark.driver.host")
      .orElse(sys.env.get("HOST"))
      .getOrElse(InetAddress.getLocalHost.getHostAddress)

  private def bindAddress(): String =
    options0.getOrElse("spark.driver.bindAddress", host())

  override def getOrCreate(): SparkSession = {

    if (isYarn() && !SparkDependencies.sparkYarnFound()) {
      println("Loading spark-yarn")
      interpApi.load.ivy(SparkDependencies.sparkYarnDependency)
    }

    if (hiveSupport() && !SparkDependencies.sparkHiveFound()) {
      println("Loading spark-hive")
      interpApi.load.ivy(SparkDependencies.sparkHiveDependency)
    }

    val sessionJars =
      replApi
        .sess
        .frames
        .flatMap(_.classpath)
        .filter(AmmoniteSparkSessionBuilder.shouldPassToSpark)
        .map(_.getAbsoluteFile.toURI.toASCIIString)

    val baseJars = AmmoniteSparkSessionBuilder.classpath(
      replApi
        .sess
        .frames
        .last
        .classloader
        .getParent
    ).map(_.toURI.toASCIIString).toVector

    val jars = (baseJars ++ sessionJars).distinct

    println("Getting spark JARs")
    val sparkJars = SparkDependencies.sparkJars(interpApi.repositories(), Nil) // interpApi.profiles().sorted)

    if (isYarn())
      config("spark.yarn.jars", sparkJars.mkString(","))

    config("spark.jars", jars.filterNot(sparkJars.toSet).mkString(","))

    val classServer = new AmmoniteClassServer(
      host(),
      bindAddress(),
      options0.get("spark.repl.class.port").fold(AmmoniteClassServer.randomPort())(_.toInt),
      replApi.sess.frames
    )
    classServerOpt = Some(classServer)

    config("spark.repl.class.uri", classServer.uri.toString)

    if (!options0.contains("spark.ui.port"))
      config("spark.ui.port", AmmoniteClassServer.availablePortFrom(4040).toString)

    if (isYarn() && !options0.contains("spark.yarn.queue"))
      for (queue <- sys.env.get("SPARK_YARN_QUEUE"))
        config("spark.yarn.queue", queue)

    def coreSiteFound =
      Thread.currentThread().getContextClassLoader.getResource("core-site.xml") != null

    def hiveSiteFound =
      Thread.currentThread().getContextClassLoader.getResource("hive-site.xml") != null

    if (isYarn() && !coreSiteFound) {

      val hadoopConfDirOpt = sys.env.get("HADOOP_CONF_DIR")
        .orElse(sys.env.get("YARN_CONF_DIR"))
        .orElse(Some(new File("/etc/hadoop/conf")).filter(_.isDirectory).map(_.getAbsolutePath))

      hadoopConfDirOpt match {
        case None =>
          println(
            "Warning: core-site.xml not found in the classpath, and no hadoop conf found via HADOOP_CONF_DIR, " +
              "YARN_CONF_DIR, or at /etc/hadoop/conf"
          )
        case Some(dir) =>
          println(s"Adding Hadoop conf dir ${AmmoniteSparkSessionBuilder.prettyDir(dir)} to classpath")
          interpApi.load.cp(ammonite.ops.Path(dir))
      }
    }

    if (hiveSupport() && !hiveSiteFound) {

      val hiveConfDirOpt = sys.env.get("HIVE_CONF_DIR")

      hiveConfDirOpt match {
        case None =>
          println("Warning: hive-site.xml not found in the classpath, and no Hive conf found via HIVE_CONF_DIR")
        case Some(dir) =>
          println(s"Adding Hive conf dir ${AmmoniteSparkSessionBuilder.prettyDir(dir)} to classpath")
          interpApi.load.cp(ammonite.ops.Path(dir))
      }
    }

    println("Creating SparkSession")
    val session = super.getOrCreate()

    interpApi.beforeExitHooks += { v =>
      if (!session.sparkContext.isStopped)
        session.sparkContext.stop()
      v
    }

    session.sparkContext.addSparkListener(
      new SparkListener {
        override def onApplicationEnd(applicationEnd: SparkListenerApplicationEnd) =
          for (s <- classServerOpt) {
            s.stop()
            classServerOpt = None
          }
      }
    )

    if (forceProgressBars0)
      AmmoniteSparkSessionBuilder.forceProgressBars(session.sparkContext)

    session
  }
}
