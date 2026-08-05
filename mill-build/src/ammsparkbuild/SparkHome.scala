package ammsparkbuild

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

import java.nio.charset.StandardCharsets

import scala.util.Using

object SparkHome {

  private def distribUrl(sparkVersion: String, hadoopVersion: String): String =
    "https://github.com/scala-cli/lightweight-spark-distrib/releases/download/v0.1.0/" +
      s"spark-$sparkVersion-bin-hadoop$hadoopVersion.tgz.tgz"

  private def download(url: String): os.Path = {
    val cache    = coursier.cache.FileCache()
    val artifact = coursier.util.Artifact(url)
    cache.file(artifact).run.unsafeRun(true)(using cache.ec) match {
      case Left(err)   => throw new Exception(s"Error downloading $url: ${err.describe}", err)
      case Right(file) => os.Path(file)
    }
  }

  // Build a local Spark distribution and return its SPARK_HOME.
  // Download a lightweight Spark distrib, run its jar-fetching script, then
  // drop Spark's REPL jar in favour of our stubs.
  def createDistributionUnder(
    workingDir: os.Path,
    sparkVersion: String,
    hadoopVersion: String,
    extraJars: Seq[os.Path]
  ): os.Path = {
    val archive = download(distribUrl(sparkVersion, hadoopVersion))

    os.proc("tar", "-zxf", archive)
      .call(cwd = workingDir, stdin = os.Inherit, stdout = os.Inherit)

    val sparkDir = os.list(workingDir)
      .filter(os.isDir)
      .find(_.last.startsWith(s"spark-$sparkVersion-"))
      .getOrElse(sys.error(s"No spark-$sparkVersion-* directory found after extracting $archive"))

    // The lightweight distrib only ships a script that downloads the actual jars.
    os.proc(sparkDir / "fetch-jars.sh")
      .call(cwd = sparkDir, stdin = os.Inherit, stdout = os.Inherit)

    // Drop Spark's own REPL jar and use our stubs in its place.
    for (jar <- os.list(sparkDir / "jars") if jar.last.startsWith("spark-repl_"))
      os.remove(jar)
    for (jar <- extraJars)
      os.copy.into(jar, sparkDir / "jars")

    sparkDir
  }

  def scalaVersionFromSparkDistribVersion(
    sparkVersion: String,
    hadoopVersion: String
  ): String = {
    // Fetch the Spark distrib like above, but don't unpack it: instead, look at the URL list that
    // fetch-jars.sh reads, find the scala-library URL in it, and get the version from that URL
    val archive     = download(distribUrl(sparkVersion, hadoopVersion))
    val jarUrlsPath = "fetch-jars/jar-urls"
    // This is called from the build definition itself, where reading files is only
    // allowed from a watched value.
    val jarUrls = mill.api.BuildCtx.withFilesystemCheckerDisabled {
      Using.resource(
        new TarArchiveInputStream(new GzipCompressorInputStream(os.read.inputStream(archive)))
      ) { tis =>
        Iterator.continually(tis.getNextEntry)
          .takeWhile(_ != null)
          .find(_.getName.endsWith("/" + jarUrlsPath))
          .map(_ => new String(tis.readAllBytes(), StandardCharsets.UTF_8))
          .getOrElse(sys.error(s"No */$jarUrlsPath entry found in $archive"))
      }
    }

    // Each line looks like https://…/scala-library-2.12.8.jar->jars/scala-library-2.12.8.jar
    val versions = jarUrls
      .linesIterator
      .map(_.split("->", 2).head.trim)
      .filter(_.nonEmpty)
      .map(_.split('/').last)
      .filter(_.startsWith("scala-library-"))
      .filter(_.endsWith(".jar"))
      .map(_.stripPrefix("scala-library-").stripSuffix(".jar"))
      .toVector
      .distinct
    assert(
      versions.nonEmpty,
      s"No scala-library URL found in $jarUrlsPath of $archive"
    )
    assert(
      versions.length == 1,
      s"Found too many scala-library URLs in $jarUrlsPath of $archive: $versions"
    )
    val v = versions.head
    assert(
      !v.startsWith("2.11."),
      s"Invalid Scala version for $sparkVersion: $v"
    )
    v
  }
}
