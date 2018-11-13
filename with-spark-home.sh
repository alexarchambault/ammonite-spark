#!/usr/bin/env bash
set -e

SPARK_VERSION="2.3.2"
HOST=localhost

BASE="$(dirname "${BASH_SOURCE[0]}")"

CACHE="${STANDALONE_CACHE:-"$BASE/target/standalone"}"
mkdir -p "$CACHE"

# Fetch spark distrib
if [ ! -d "$CACHE/spark-$SPARK_VERSION-"* ]; then
  TRANSIENT_SPARK_ARCHIVE=0
  if [ ! -e "$CACHE/archive/spark-$SPARK_VERSION-"*.tgz ]; then
    mkdir -p "$CACHE/archive"
    TRANSIENT_SPARK_ARCHIVE=1
    curl -Lo "$CACHE/archive/spark-$SPARK_VERSION-bin-hadoop2.7.tgz" "https://archive.apache.org/dist/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop2.7.tgz"
  fi

  ( cd "$CACHE" && tar -zxvf "archive/spark-$SPARK_VERSION-"*.tgz )
  test "$TRANSIENT_SPARK_ARCHIVE" = 0 || rm -f "$CACHE/archive/spark-$SPARK_VERSION-"*.tgz
  rmdir -p "$CACHE/archive" 2>/dev/null || true
fi


export SPARK_HOME="$(cd "$CACHE/spark-$SPARK_VERSION-"*; pwd)"
export SPARK_VERSION

exec "$@"
