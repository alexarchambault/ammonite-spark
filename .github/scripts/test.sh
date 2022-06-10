#!/usr/bin/env bash
set -e

case "${MASTER:-"local"}" in
  local)
    ./sbt +publishLocal +test +mimaReportBinaryIssues ;;
  local-distrib)
    ./with-spark-home.sh ./sbt +publishLocal +local-spark-distrib-tests/test ;;
  standalone)
    ./with-spark-home.sh ./sbt-with-standalone-cluster.sh +publishLocal +standalone-tests/test ;;
  yarn)
    ./sbt-in-docker-with-yarn-cluster.sh -batch +publishLocal +yarn-tests/test ;;
  yarn-distrib)
    ./with-spark-home.sh ./sbt-in-docker-with-yarn-cluster.sh -batch +publishLocal +yarn-spark-distrib-tests/test ;;
  *)
    echo "Unrecognized master type $MASTER"
    exit 1
    ;;
esac
