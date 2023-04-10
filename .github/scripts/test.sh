#!/usr/bin/env bash
set -e

case "${MASTER:-"local"}" in
  local)
    ./mill __.publishLocal
    ./mill 'tests[_].test'
    ./mill __.mimaReportBinaryIssues
    ;;
  local-distrib)
    ./with-spark-home.sh ./mill __.publishLocal "+" 'local-spark-distrib-tests.test' ;;
  standalone)
    ./with-spark-home.sh ./mill-with-standalone-cluster.sh __.publishLocal "+" 'standalone-tests.test' ;;
  yarn)
    ./mill-in-docker-with-yarn-cluster.sh __.publishLocal "+" 'yarn-tests[_].test' ;;
  yarn-distrib)
    ./with-spark-home.sh ./mill-in-docker-with-yarn-cluster.sh __.publishLocal "+" yarn-spark-distrib-tests.test ;;
  *)
    echo "Unrecognized master type $MASTER"
    exit 1
    ;;
esac
