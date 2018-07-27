Run the tests with `local` master with
```
$ sbt publishLocal test
```

Run the tests with against a standalone cluster with
```
$ ./sbt-with-standalone-cluster.sh publishLocal standalone-tests/test
```
Note that this command downloads a Spark distribution itself, starts a master and one slave, and shuts them down when the command exits.

Run the tests against a YARN cluster with
```
$ ./sbt-in-docker-with-yarn-cluster.sh publishLocal yarn-tests/test
```
Note that SBT is run inside a docker container in that case. This commands starts a dockerized single-node YARN cluster, and shuts it down upon exit.