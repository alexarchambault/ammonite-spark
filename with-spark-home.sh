#!/usr/bin/env bash
set -e

SPARK_VERSION="2.4.2" # only 2.4 version published with scala 2.12
HOST=localhost

BASE="$(dirname "${BASH_SOURCE[0]}")"

CACHE="${STANDALONE_CACHE:-"$BASE/target/standalone"}"
mkdir -p "$CACHE"

SPARK_STUBS_JAR="$(./mill show 'spark-stubs_24.jar' | jq -r . | sed 's/ref:[a-f0-9]*://')"

# Fetch spark distrib
if [ ! -d "$CACHE/spark-$SPARK_VERSION-"* ]; then
  TRANSIENT_SPARK_ARCHIVE=0
  if [ ! -e "$CACHE/archive/spark-$SPARK_VERSION-"*.tgz ]; then
    mkdir -p "$CACHE/archive"
    TRANSIENT_SPARK_ARCHIVE=1
    curl -Lo "$CACHE/archive/spark-$SPARK_VERSION-bin-hadoop2.7.tgz" "https://github.com/scala-cli/lightweight-spark-distrib/releases/download/v0.0.4/spark-$SPARK_VERSION-bin-hadoop2.7-scala2.12.tgz"
  fi

  ( cd "$CACHE" && tar -zxf "archive/spark-$SPARK_VERSION-"*.tgz && "spark-$SPARK_VERSION-"*/fetch-jars.sh && rm -f "spark-$SPARK_VERSION-"*/jars/spark-repl_*.jar && cp "$SPARK_STUBS_JAR" "spark-$SPARK_VERSION-"*/jars/ )
  test "$TRANSIENT_SPARK_ARCHIVE" = 0 || rm -f "$CACHE/archive/spark-$SPARK_VERSION-"*.tgz
  rmdir -p "$CACHE/archive" 2>/dev/null || true
fi


export SPARK_HOME="$(cd "$CACHE/spark-$SPARK_VERSION-"*; pwd)"
export SPARK_VERSION

echo "SPARK_HOME=$SPARK_HOME"
echo "SPARK_VERSION=$SPARK_VERSION"

exec "$@"
