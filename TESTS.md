Run the tests with `local` master with
```
$ ./mill local-tests._.testForked
```

Run the tests against **Spark 4** (`local[*]` master, Scala 2.13.16, on Java 17 and 21) with
```
$ ./mill local-tests-spark4._.testForked   # both JDKs; or local-tests-spark4[17] / [21]
```
This runs the same shared `test-definitions` suite against Spark 4.0.3 using the Spark-4 artifacts
(`ammonite-spark-spark4`, built against upstream Ammonite 3.0.8 / almond 0.14.5). Spark 4 supports
Java 17 and 21, so the module is cross-built over both. See `ARCHITECTURE.md` §6 for how Spark 4
support is structured.

To exercise the 4.0.0-compiled artifacts against a different Spark 4.x runtime (e.g. to check
forward compatibility with 4.1.x / 4.2.x), set `AMMSPARK_SPARK_VERSION` — the same variable the
Spark ≤ 3 test modules get from their cross value:
```
$ AMMSPARK_SPARK_VERSION=4.1.3 ./mill 'local-tests-spark4[21].testForked'
```
The fork already passes `--enable-native-access=ALL-UNNAMED` (part of Spark 4.1+'s module options),
so 4.1.x / 4.2.x runs don't emit native-access warnings.

Run the tests with against a standalone cluster with
```
$ ./mill standalone-tests.testForked
```
Note that this command downloads a Spark distribution itself, starts a master and one slave, and shuts them down when the command exits.

Run the tests against a YARN cluster with
```
$ ./mill-in-docker-with-yarn-cluster.sh yarn-tests.__.testForked
```

Run the tests against a YARN cluster _using a provided spark distribution_ with
```
$ ./mill-in-docker-with-yarn-cluster.sh yarn-spark-distrib-tests.testForked
```
Note that Mill is run inside a docker container in the last two cases. These commands starts a dockerized single-node YARN cluster, and shut it down upon exit.

## Spark logs

By default, the tests only write the Spark logs to a `spark.log` file. To also
get them on the console (at `INFO` level), which helps debugging, set the
`SPARK_LOG_CONSOLE` environment variable, e.g.
```
$ SPARK_LOG_CONSOLE=1 ./mill local-tests.__.testForked
$ SPARK_LOG_CONSOLE=1 ./mill-in-docker-with-yarn-cluster.sh yarn-tests.__.testForked
```
The variable is forwarded into the docker container for the YARN tests.
