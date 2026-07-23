
## How is the spark classpath handled?

The classpath is entirely managed from Ammonite. You don't need a spark distribution / a `SPARK_HOME`. The Ammonite process corresponds to the Spark driver. The classpath you can access from the session is the one of the driver. Spark is loaded in the driver when the user loads it, e.g. via ``import $ivy.`org.apache.spark::spark-sql:2.3.1` ``. You can load the Spark version of your choice this way.

The executor classpath depends on the cluster manager used:

`AmmoniteSparkSession.builder()` first computes a classpath that consists in the various Spark modules that are loaded in the Ammonnite session (`spark-sql`, `spark-mllib`, etc. if these were loaded byt the user), along with their transitive dependencies. If YARN is used as cluster manager, it adds `spark-yarn` to them and passes this classpath in `"spark.yarn.jars"` in the SparkConf.

`AmmoniteSparkSession.builder()` then computes a second classpath, made of all the JARs that were loaded in the session and those that come from Ammonite itself, and removes from this list the JARs already in the first classpath above (even with non YARN clusters). These JARs are passed to Spark via `"spark.jars"` in the SparkConf.

The code entered by the user during the session is compiled and results in extra classes. These are accessible via a small web server that `AmmoniteSparkSession.builder()` launches, and whose address is passed to Spark via `"spark.repl.class.uri"` in the SparkConf.

## `AmmoniteSparkSession` vs `SparkSession`

The builder created via `AmmoniteSparkSession.builder()` extends the one from `SparkSession.builder()`. It does a number of things more compared to it.

- if master is set to `yarn`, it loads `spark-yarn` in the Ammonite session, sets `"spark.yarn.jars"` in the Spark conf, and tries to add the YARN conf to the classpath if it's not already there (it successively looks at `HADOOP_CONF_DIR` and `YARN_CONF_DIR` in the environment, and in `/etc/hadoop/conf`)

- if `"spark.sql.catalogImplementation"` is set to `"hive"` in the SparkConf, it loads `spark-hive` in the Ammonite session, and tries to add the Hive conf to the classpath if it's not already there (looking at `HIVE_CONF_DIR` in the environment)

- it adds the JARs loaded in the session to `"spark.jars"` in the SparkConf (excluding those that come from Spark modules, or are pulled transitively by them)

- it starts a small web server that exposes the classes resulting from compiling the code entered at the Ammonite prompt, and passes its address to Spark via `"spark.repl.class.uri"` in the SparkConf.

- one can call `.progressBars()` on the builder to force Spark to display progress bars in the console

## Spark 4

Spark 4 support is provided by a separate, Scala-2.13-only set of artifacts (`ammonite-spark-spark4`,
`almond-spark-spark4`, `almond-toree-spark-spark4`) that coexist with the Spark ≤ 3 ones. The jar /
`spark.jars` / `spark.yarn.jars` machinery above is unchanged, but two things differ, because Spark 4
refactored the relevant internals (see `ARCHITECTURE.md` §6 for the full details):

- **REPL classes reach the executors differently.** Spark 4's executor-side class loader
  (`org.apache.spark.executor.ExecutorClassLoader`, now in `spark-core`) dropped the HTTP fetch path,
  so the small web server / `spark.repl.class.uri` approach isn't used. Instead the builder sets
  `spark.repl.class.outputDir` to the directory Ammonite writes compiled cell classes to, and Spark's
  own driver class-file server serves them to executors over its RPC channel. This means **Ammonite
  must be started with an output directory** (`--tmp-output-directory`; almond ≥ 0.14 does this by
  default) — the Spark-4 builder fails fast with an explanatory message otherwise. No `spark-stubs_*`
  is used or needed on Spark 4.

- **The builder extends the *classic* `SparkSession.Builder`.** Spark 4 split `SparkSession` into an
  abstract `org.apache.spark.sql.SparkSession` and a concrete `org.apache.spark.sql.classic.SparkSession`;
  only the classic builder creates a driver-backed session, so the Spark-4 builder extends that one.
