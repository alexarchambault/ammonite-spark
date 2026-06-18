Run the tests with `local` master with
```
$ ./mill local-tests._.testForked
```

Run the tests with against a standalone cluster with
```
$ ./mill standalone-tests.testForked
```
Note that this command downloads a Spark distribution itself, starts a master and one slave, and shuts them down when the command exits.

Run the tests against a YARN cluster with
```
$ ./mill-in-docker-with-yarn-cluster.sh yarn-tests._.testForked
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
$ SPARK_LOG_CONSOLE=1 ./mill local-tests._.testForked
$ SPARK_LOG_CONSOLE=1 ./mill-in-docker-with-yarn-cluster.sh yarn-tests._.testForked
```
The variable is forwarded into the docker container for the YARN tests.
