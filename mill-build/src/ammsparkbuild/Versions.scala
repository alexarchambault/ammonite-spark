package ammsparkbuild

object Versions {
  def scala212 = "2.12.11"
  def scala213 = "2.13.11"

  def scala = Seq(scala213, scala212)

  // Spark 4 is Scala 2.13-only. The Spark-4 modules are built against the versions almond 0.14.5
  // uses: Scala 2.13.16 and upstream Ammonite 3.0.8 (com.lihaoyi, not the sh.almond.tmp.ammonite
  // fork the rest of the build pins). The Spark <= 3 modules stay on scala213 / the fork / `almond`
  // above.
  def scala213Spark4 = "2.13.16"
  def ammoniteSpark4 = "3.0.8"
  def almondSpark4   = "0.14.5"
  // Compile target for the Spark-4 modules: the *oldest* Spark 4.x we support, so the published
  // bytecode never references an API added in a later 4.0.x patch and thus runs on all 4.0.x (and,
  // verified binary-compatible against 4.1.3 and 4.2.0, later 4.x). This is only the compile
  // version — the Spark-4 tests run against spark4Test below, proving the 4.0.0-compiled artifacts
  // work on a newer 4.x.
  def spark4 = "4.0.0"
  // Spark version the Spark-4 tests resolve at runtime. Overridable per run with the
  // AMMSPARK_SPARK_VERSION env var the test definitions already read, e.g. to check the
  // 4.0.0-compiled artifacts against 4.1.x / 4.2.x.
  def spark4Test = "4.0.3"

  def almond        = "0.14.0-RC13"
  def ammonite      = "3.0.0-M0-67-83057fea"
  def jsoniterScala = "2.13.5"
}
