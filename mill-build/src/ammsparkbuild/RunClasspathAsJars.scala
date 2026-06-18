package ammsparkbuild

import mill.*
import mill.scalalib.*

trait RunClasspathAsJars extends JavaModule {
  // ammonite-spark ships the REPL's runtime classpath to the Spark executors via
  // sc.addJar, which only accepts JARs - directory entries are rejected with
  // "Failed to add file:.../classes/ to Spark environment". Repackage every
  // directory on the run classpath into a JAR so the whole classpath is JARs.
  def runClasspath = Task {
    super.runClasspath().zipWithIndex.map {
      case (ref, idx) =>
        if (os.isDir(ref.path)) {
          val jar = Task.dest / s"run-$idx-${ref.path.last}.jar"
          mill.util.Jvm.createJar(jar, Seq(ref.path))
          PathRef(jar)
        }
        else
          ref
    }
  }
}
