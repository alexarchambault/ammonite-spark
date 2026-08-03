#!/usr/bin/env bash
set -e

case "${MASTER:-"local"}" in
  local)
    ./mill local-tests.__.testForked
    ./mill __.mimaReportBinaryIssues
    ;;
  local-distrib)
    ./mill local-spark-distrib-tests._.testForked ;;
  standalone)
    ./mill standalone-tests._.testForked ;;
  yarn)
    ./mill-in-docker-with-yarn-cluster.sh --prefetch 'yarn-tests._.testForked' ;;
  yarn-distrib)
    ./mill-in-docker-with-yarn-cluster.sh yarn-spark-distrib-tests._.testForked ;;
  *)
    echo "Unrecognized master type $MASTER"
    exit 1
    ;;
esac
