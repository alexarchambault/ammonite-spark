#!/usr/bin/env bash
set -e

SPARK_VERSION="2.4.2" # only 2.4 version published with scala 2.12
HOST=localhost

BASE="$(dirname "${BASH_SOURCE[0]}")"

CACHE="${STANDALONE_CACHE:-"$BASE/target/standalone"}"
mkdir -p "$CACHE"

if ! ls modules/spark-stubs_24/target/scala-2.12/spark-stubs_24_2.12-*.jar >/dev/null 2>&1; then
  sbt spark-stubs_24/packageBin
fi
SPARK_STUBS_JAR="$(ls "$(pwd)/modules/spark-stubs_24/target/scala-2.12/spark-stubs_24_2.12-"*.jar)"

# Fetch spark distrib
if [ ! -d "$CACHE/spark-$SPARK_VERSION-"* ]; then
  TRANSIENT_SPARK_ARCHIVE=0
  if [ ! -e "$CACHE/archive/spark-$SPARK_VERSION-"*.tgz ]; then
    mkdir -p "$CACHE/archive"
    TRANSIENT_SPARK_ARCHIVE=1
    curl -Lo "$CACHE/archive/spark-$SPARK_VERSION-bin-hadoop2.7.tgz" "https://archive.apache.org/dist/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop2.7.tgz"
  fi

  ( cd "$CACHE" && tar -zxvf "archive/spark-$SPARK_VERSION-"*.tgz && rm -f "spark-$SPARK_VERSION-"*/jars/spark-repl_*.jar && cp "$SPARK_STUBS_JAR" "spark-$SPARK_VERSION-"*/jars/ )
  test "$TRANSIENT_SPARK_ARCHIVE" = 0 || rm -f "$CACHE/archive/spark-$SPARK_VERSION-"*.tgz
  rmdir -p "$CACHE/archive" 2>/dev/null || true
fi


export SPARK_HOME="$(cd "$CACHE/spark-$SPARK_VERSION-"*; pwd)"
export SPARK_VERSION

exec "$@"
