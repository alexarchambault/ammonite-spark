#!/usr/bin/env bash
set -eu

HOST=localhost

cd "$(dirname "${BASH_SOURCE[0]}")"

cleanup() {
  cd "$SPARK_HOME"
  ./sbin/stop-slave.sh || true
  ./sbin/stop-master.sh || true
}

trap cleanup EXIT INT TERM

SPARK_MASTER="spark://$HOST:7077"

( cd "$SPARK_HOME" && ./sbin/start-master.sh --host "$HOST" )
( cd "$SPARK_HOME" && ./sbin/start-slave.sh --host "$HOST" "$SPARK_MASTER" -c 4 -m 4g )

STANDALONE_SPARK_MASTER="$SPARK_MASTER" \
  STANDALONE_SPARK_VERSION="$SPARK_VERSION" \
  ./sbt "$@"
