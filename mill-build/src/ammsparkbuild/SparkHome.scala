package ammsparkbuild

import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

import scala.util.Using

object SparkHome {
  // Build a local Spark distribution and return its SPARK_HOME.
  // Download a lightweight Spark distrib, run its jar-fetching script, then
  // drop Spark's REPL jar in favour of our stubs.
  def createDistributionUnder(
    workingDir: os.Path,
    sparkVersion: String,
    extraJars: Seq[os.Path],
    scalaVersion: String = ""
  ): os.Path = {
    val url =
      "https://github.com/scala-cli/lightweight-spark-distrib/releases/download/v0.0.4/" +
        s"spark-$sparkVersion-bin-hadoop2.7-scala2.12.tgz"

    val cache    = coursier.cache.FileCache()
    val artifact = coursier.util.Artifact(url)
    val archive = cache.file(artifact).run.unsafeRun(true)(using cache.ec) match {
      case Left(err)   => throw new Exception(s"Error downloading $url: ${err.describe}", err)
      case Right(file) => os.Path(file)
    }

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

    if (scalaVersion.nonEmpty) {
      val scalaVersionFromLibraryJar = {
        val libraryJars = os.list(sparkDir / "jars")
          .filter(_.last.startsWith("scala-library"))
          .filter(_.last.endsWith(".jar"))
          .filter(os.isFile)
        assert(
          libraryJars.nonEmpty,
          s"No scala-library*.jar found under ${sparkDir / "jars"}"
        )
        assert(
          libraryJars.length == 1,
          s"Found too many scala-library*.jar files under ${sparkDir / "jars"}: ${libraryJars.map(_.subRelativeTo(sparkDir / "jars"))}"
        )
        val libraryJar = libraryJars.head
        val foundVersion =
          Using.resource(new ZipFile(libraryJar.toIO)) { zf =>
            val ent = zf.getEntry("library.properties")
            assert(ent != null, s"library.properties not found in $libraryJar")
            val content = new String(zf.getInputStream(ent).readAllBytes(), StandardCharsets.UTF_8)
            content
              .linesIterator
              .find(_.startsWith("version.number="))
              .map(_.stripPrefix("version.number=").trim())
              .getOrElse {
                sys.error(s"No version.number=... line found in library.properties in $libraryJar")
              }
          }
        assert(
          scalaVersion == foundVersion,
          s"Found Scala version $foundVersion in $libraryJar, expected $scalaVersion"
        )
      }
    }

    sparkDir
  }
}
