import $file.project.deps, deps.{Deps, Versions}

import $ivy.`com.github.lolgab::mill-mima::0.0.23`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`

import com.github.lolgab.mill.mima.Mima
import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._
import mill.scalalib._

import java.util.Arrays

import scala.concurrent.duration.DurationInt

// Tell mill modules are under modules/
implicit def millModuleBasePath: define.BasePath =
  define.BasePath(super.millModuleBasePath.value / "modules")

trait AmmSparkPublishModule extends PublishModule {
  import mill.scalalib.publish._
  def publishVersion = T {
    val v        = VcsVersion.vcsState().format()
    val dirtyIdx = v.indexOf("-DIRTY")
    def endsWithCommitHash =
      v.length > 6 && v.substring(v.length - 6).forall(c => c.isDigit || (c >= 'a' && c <= 'f'))
    if (dirtyIdx >= 0) v.take(dirtyIdx) + "-SNAPSHOT"
    else if (endsWithCommitHash) v + "-SNAPSHOT"
    else v
  }
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "sh.almond",
    url = "https://github.com/alexarchambault/ammonite-spark.git",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("alexarchambault", "ammonite-spark"),
    developers = Seq(
      Developer("alexarchambault", "Alex Archambault", "https://github.com/alexarchambault")
    )
  )
}

trait AmmSparkMima extends Mima {
  // same as https://github.com/lolgab/mill-mima/blob/de28f3e9fbe92867f98e35f8dfd3c3a777cc033d/mill-mima/src/com/github/lolgab/mill/mima/Mima.scala#L29-L44
  // except we're ok if mimaPreviousVersions is empty
  def mimaPreviousArtifacts = T {
    val versions = mimaPreviousVersions().distinct
    mill.api.Result.Success(
      Agg.from(
        versions.map(version =>
          ivy"${pomSettings().organization}:${artifactId()}:$version"
        )
      )
    )
  }
}

trait WithPropertyFile extends JavaModule {
  def versionInProperties: T[String]
  def propertyFilePath: os.SubPath
  def propResourcesDir = T.persistent {
    import sys.process._

    val dir = T.dest / "property-resources"
    val ver = versionInProperties()

    val f = dir / propertyFilePath

    val contentStr =
      s"""commit-hash=${Seq("git", "rev-parse", "HEAD").!!.trim}
         |version=$ver
         |""".stripMargin

    val content = contentStr.getBytes("UTF-8")

    if (!os.exists(f) || !Arrays.equals(content, os.read.bytes(f))) {
      os.write.over(f, content, createFolders = true)
      System.err.println(s"Wrote $f")
    }

    PathRef(dir)
  }
  def resources = T.sources {
    super.resources() ++ Seq(propResourcesDir())
  }
}

trait WithDependencyResourceFile extends JavaModule {
  def dependencyResourcePath: os.SubPath

  def dependencyFileResources = T.persistent {

    val dir  = T.dest / "dep-file"
    val dest = dir / dependencyResourcePath

    val deps0 = T.task(compileIvyDeps() ++ transitiveIvyDeps())()
    val (_, res) = mill.modules.Jvm.resolveDependenciesMetadata(
      repositoriesTask(),
      deps0.map(resolveCoursierDependency().apply(_)),
      deps0.filter(_.force).map(resolveCoursierDependency().apply(_)),
      mapDependencies = Some(mapDependencies())
    )

    val content = res.minDependencies.toSeq
      .map(dep => (dep.module.organization.value, dep.module.name.value, dep.version))
      .sorted
      .map {
        case (org, name, ver) =>
          s"$org:$name:$ver"
      }
      .mkString("\n")
      .getBytes("UTF-8")

    if (!os.exists(dest) || !Arrays.equals(content, os.read.bytes(dest))) {
      os.write.over(dest, content, createFolders = true)
      System.err.println(s"Wrote $dest")
    }

    Seq(PathRef(dir))
  }

  def resources = T.sources {
    super.resources() ++ dependencyFileResources()
  }
}

object `spark-stubs_24` extends SbtModule with AmmSparkPublishModule {
  def scalaVersion = Versions.scala212
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.sparkSql(scalaVersion())
  )
}

object `spark-stubs_30` extends SbtModule with AmmSparkPublishModule {
  def scalaVersion = Versions.scala212
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.sparkSql3
  )
}

object `spark-stubs_32` extends Cross[SparkStubs32](Versions.scala)
trait SparkStubs32      extends CrossSbtModule
    with AmmSparkPublishModule {
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.sparkSql32
  )
}

object core extends Cross[Core](Versions.scala)
trait Core  extends CrossSbtModule with WithPropertyFile
    with AmmSparkPublishModule with AmmSparkMima {
  def artifactName = "ammonite-spark"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.ammoniteReplApi,
    Deps.sparkSql(scalaVersion())
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.classPathUtil,
    Deps.jettyServer
  )
  def propertyFilePath =
    os.sub / "org" / "apache" / "spark" / "sql" / "ammonitesparkinternals" / "ammonite-spark.properties"
  def versionInProperties = publishVersion()
  def propResourcesDir = T.persistent {
    import sys.process._

    val dir = T.dest / "property-resources"
    val ver = publishVersion()

    val f =
      dir / "org" / "apache" / "spark" / "sql" / "ammonitesparkinternals" / "ammonite-spark.properties"

    val contentStr =
      s"""commit-hash=${Seq("git", "rev-parse", "HEAD").!!.trim}
         |version=$ver
         |""".stripMargin

    val content           = contentStr.getBytes("UTF-8")
    val currentContentOpt = if (os.exists(f)) Some(os.read.bytes(f)) else None

    if (!os.exists(f) || !Arrays.equals(content, os.read.bytes(f))) {
      os.write.over(f, content, createFolders = true)
      System.err.println(s"Wrote $f")
    }

    PathRef(dir)
  }
  def resources = T.sources {
    super.resources() ++ Seq(propResourcesDir())
  }

  def mimaPreviousVersions = T {
    val needs =
      if (scalaVersion().startsWith("2.12.")) "v0.9.0"
      else "v0.13.0"
    os.proc("git", "tag", "--merged", "HEAD^", "--contains", needs)
      .call()
      .out.lines()
      .map(_.trim)
      .filter(_.startsWith("v"))
      .map(_.stripPrefix("v"))
  }
}

trait AmmSparkTests extends TestModule {
  def testFramework = "utest.runner.Framework"
  def forkArgs      = super.forkArgs() ++ Seq("-Xmx3g", "-Dfoo=bzz")
}

object tests extends Cross[Tests](Versions.scala)
trait Tests  extends CrossSbtModule with WithPropertyFile
    with WithDependencyResourceFile {
  def propertyFilePath       = os.sub / "ammonite" / "ammonite-spark.properties"
  def versionInProperties    = core().publishVersion()
  def dependencyResourcePath = os.sub / "ammonite" / "spark" / "amm-test-dependencies.txt"

  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.ammoniteCompiler.exclude(("com.google.guava", "guava")),
    Deps.ammoniteRepl.exclude(("com.google.guava", "guava")),
    Deps.utest
  )

  object test extends Tests with AmmSparkTests
}

object `local-spark-distrib-tests` extends SbtModule {
  private def sv   = Versions.scala212
  def scalaVersion = sv
  def moduleDeps = super.moduleDeps ++ Seq(
    tests(sv)
  )

  object test extends Tests with AmmSparkTests
}

object `standalone-tests` extends SbtModule {
  private def sv   = Versions.scala212
  def scalaVersion = sv
  def moduleDeps = super.moduleDeps ++ Seq(
    tests(sv)
  )

  object test extends Tests with AmmSparkTests
}

object `yarn-tests` extends Cross[YarnTests](Versions.scala)
trait YarnTests extends CrossSbtModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    tests()
  )

  object test extends Tests with AmmSparkTests
}

object `yarn-spark-distrib-tests` extends SbtModule {
  private def sv   = Versions.scala212
  def scalaVersion = sv
  def moduleDeps = super.moduleDeps ++ Seq(
    tests(sv)
  )

  object test extends Tests with AmmSparkTests
}

object `almond-spark` extends Cross[AlmondSpark](Versions.scala)
trait AlmondSpark     extends CrossSbtModule with AmmSparkPublishModule
    with AmmSparkMima {
  def moduleDeps = super.moduleDeps ++ Seq(
    core()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.jsoniterScalaCore
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.ammoniteReplApi,
    Deps.jsoniterScalaMacros,
    Deps.log4j2,
    Deps.scalaKernelApi,
    Deps.sparkSql(scalaVersion())
  )
  def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      coursier.Repositories.jitpack
    )
  }
}

object ci extends Module {
  def publishSonatype(tasks: mill.main.Tasks[PublishModule.PublishData]) = T.command {
    publishSonatype0(
      data = define.Target.sequence(tasks.value)(),
      log = T.ctx().log
    )
  }

  def publishSonatype0(
    data: Seq[PublishModule.PublishData],
    log: mill.api.Logger
  ): Unit = {

    val credentials = sys.env("SONATYPE_USERNAME") + ":" + sys.env("SONATYPE_PASSWORD")
    val pgpPassword = sys.env("PGP_PASSPHRASE")
    val timeout     = 10.minutes

    val artifacts = data.map {
      case PublishModule.PublishData(a, s) =>
        (s.map { case (p, f) => (p.path, f) }, a)
    }

    val isRelease = {
      val versions = artifacts.map(_._2.version).toSet
      val set      = versions.map(!_.endsWith("-SNAPSHOT"))
      assert(
        set.size == 1,
        s"Found both snapshot and non-snapshot versions: ${versions.toVector.sorted.mkString(", ")}"
      )
      set.head
    }
    val publisher = new scalalib.publish.SonatypePublisher(
      uri = "https://oss.sonatype.org/service/local",
      snapshotUri = "https://oss.sonatype.org/content/repositories/snapshots",
      credentials = credentials,
      signed = true,
      // format: off
      gpgArgs = Seq(
        "--detach-sign",
        "--batch=true",
        "--yes",
        "--pinentry-mode", "loopback",
        "--passphrase", pgpPassword,
        "--armor",
        "--use-agent"
      ),
      // format: on
      readTimeout = timeout.toMillis.toInt,
      connectTimeout = timeout.toMillis.toInt,
      log = log,
      awaitTimeout = timeout.toMillis.toInt,
      stagingRelease = isRelease
    )

    publisher.publishAll(isRelease, artifacts: _*)
  }
}
