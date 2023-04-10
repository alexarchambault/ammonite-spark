Run the tests with `local` master with
```
$ ./mill __.publishLocal "+" 'tests[_].test'
```

Run the tests with against a standalone cluster with
```
$ ./mill-with-standalone-cluster.sh __.publishLocal "+" standalone-tests.test
```
Note that this command downloads a Spark distribution itself, starts a master and one slave, and shuts them down when the command exits.

Run the tests against a YARN cluster with
```
$ ./mill-in-docker-with-yarn-cluster.sh __.publishLocal "+" 'yarn-tests[_].test'
```

Run the tests against a YARN cluster _using a provided spark distribution_ with
```
$ ./with-spark-home.sh ./mill-in-docker-with-yarn-cluster.sh __.publishLocal "+" yarn-spark-distrib-tests.test
```
Note that Mill is run inside a docker container in the last two cases. These commands starts a dockerized single-node YARN cluster, and shut it down upon exit.
