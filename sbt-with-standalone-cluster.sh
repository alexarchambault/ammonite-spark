#!/usr/bin/env bash
set -eu

HOST=localhost

cd "$(dirname "${BASH_SOURCE[0]}")"

SPARK_HOME="$(./with-spark-home.sh /bin/bash -c 'echo $SPARK_HOME')"
SPARK_VERSION="$(./with-spark-home.sh /bin/bash -c 'echo $SPARK_VERSION')"

cleanup() {
  "$SPARK_HOME/sbin/stop-slave.sh" || true
  "$SPARK_HOME/sbin/stop-master.sh" || true
}

trap cleanup EXIT INT TERM

SPARK_MASTER="spark://$HOST:7077"

"$SPARK_HOME/sbin/start-master.sh" --host "$HOST"
"$SPARK_HOME/sbin/start-slave.sh" --host "$HOST" "$SPARK_MASTER" -c 4 -m 4g

STANDALONE_SPARK_MASTER="$SPARK_MASTER" \
  STANDALONE_SPARK_VERSION="$SPARK_VERSION" \
  sbt "$@"
