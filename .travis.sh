#!/usr/bin/env bash
set -e

case "${MASTER:-"local"}" in
  local)
    sbt ++$TRAVIS_SCALA_VERSION'!' publishLocal test ;;
  standalone)
    ./sbt-with-standalone-cluster.sh ++$TRAVIS_SCALA_VERSION'!' publishLocal standalone-tests/test ;;
  yarn)
    ./sbt-in-docker-with-yarn-cluster.sh -batch ++$TRAVIS_SCALA_VERSION'!' publishLocal yarn-tests/test ;;
  yarn-distrib)
    ./with-spark-home.sh ./sbt-in-docker-with-yarn-cluster.sh -batch ++$TRAVIS_SCALA_VERSION'!' publishLocal yarn-spark-distrib-tests/test ;;
  *)
    echo "Unrecognized master type $MASTER"
    exit 1
    ;;
esac
