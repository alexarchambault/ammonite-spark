#!/usr/bin/env bash
set -e

SPARK_VERSION="2.3.1"
HOST=localhost

cd "$(dirname "${BASH_SOURCE[0]}")"

mkdir -p target/standalone

# Fetch spark distrib
if [ ! -d target/standalone/spark-* ]; then
  TRANSIENT_SPARK_ARCHIVE=0
  if [ ! -e target/standalone/archive/spark-*.tgz ]; then
    mkdir -p target/standalone/archive
    TRANSIENT_SPARK_ARCHIVE=1
    curl -Lo "target/standalone/archive/spark-$SPARK_VERSION-bin-hadoop2.7.tgz" "https://archive.apache.org/dist/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop2.7.tgz"
  fi

  ( cd target/standalone && tar -zxvf archive/spark-*.tgz )
  test "$TRANSIENT_SPARK_ARCHIVE" = 0 || rm -f target/standalone/archive/spark-*.tgz
  rmdir -p target/standalone/archive 2>/dev/null || true
fi


DIR="$(pwd)"

cleanup() {
  cd "$DIR/target/standalone/spark-"*
  sbin/stop-slave.sh || true
  sbin/stop-master.sh || true
}

trap cleanup EXIT INT TERM


SPARK_MASTER="spark://$HOST:7077"

cd target/standalone/spark-*
sbin/start-master.sh --host "$HOST"
sbin/start-slave.sh --host "$HOST" "$SPARK_MASTER" -c 4 -m 4g
cd ../../..

STANDALONE_SPARK_MASTER="$SPARK_MASTER" \
  STANDALONE_SPARK_VERSION="$SPARK_VERSION" \
  sbt "$@"
