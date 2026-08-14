#!/usr/bin/env bash
set -eu

# when the tests are running, open the YARN UI at http://localhost:8088

# --prefetch SPARK_VERSION pre-fetches the Spark dependencies of that version in
# the container (before the tests pull them through coursier), so that download
# failures are easier to tell apart from actual test failures.
# --scala-version SCALA_VERSION tells which Scala version those dependencies
# should be fetched for (only its binary version matters), and is required
# alongside --prefetch.
# --hadoop-version MAJOR selects the Hadoop major version of the YARN cluster.
# Supported values are 2 (the default) and 3.
# --jvm-version VERSION selects the JVM used by Mill and the forked tests.
PREFETCH_SPARK_VERSION=""
SCALA_VERSION=""
HADOOP_MAJOR_VERSION=2
JVM_VERSION=11

while [ "$#" -gt 0 ]; do
  case "$1" in
    --prefetch)
      shift
      if [ "$#" = 0 ]; then
        echo "--prefetch expects a Spark version" 1>&2
        exit 1
      fi
      PREFETCH_SPARK_VERSION="$1"
      shift
      ;;
    --scala-version)
      shift
      if [ "$#" = 0 ]; then
        echo "--scala-version expects a Scala version" 1>&2
        exit 1
      fi
      SCALA_VERSION="$1"
      shift
      ;;
    --hadoop-version)
      shift
      if [ "$#" = 0 ]; then
        echo "--hadoop-version expects 2 or 3" 1>&2
        exit 1
      fi
      HADOOP_MAJOR_VERSION="$1"
      shift
      ;;
    --jvm-version)
      shift
      if [ "$#" = 0 ]; then
        echo "--jvm-version expects a JVM version" 1>&2
        exit 1
      fi
      JVM_VERSION="$1"
      shift
      ;;
    *)
      break
      ;;
  esac
done

case "$HADOOP_MAJOR_VERSION" in
  2)
    YARN_CLUSTER_IMAGE="docker.io/alexarchambault/yarn-cluster"
    ;;
  3)
    HADOOP_3_VERSION=3.3.6
    YARN_CLUSTER_IMAGE="ammonite-spark/yarn-cluster:hadoop-$HADOOP_3_VERSION-jvm-$JVM_VERSION"
    ;;
  *)
    echo "--hadoop-version expects 2 or 3, got '$HADOOP_MAJOR_VERSION'" 1>&2
    exit 1
    ;;
esac

if [ -n "$PREFETCH_SPARK_VERSION" ] && [ -z "$SCALA_VERSION" ]; then
  echo "--prefetch requires --scala-version" 1>&2
  exit 1
fi

INTERACTIVE=0
if [ -t 1 ]; then
  INTERACTIVE=1
fi

cd "$(dirname "${BASH_SOURCE[0]}")"


# this name can't be changed (hardcoded in stuff in the yarn-cluster image)
NAMENODE=namenode


dump_yarn_logs() {
  # The driver-side logs only show the SparkContext being shut down by the YARN
  # ApplicationMaster monitor thread - the actual failure is on the YARN side.
  # yarn.nodemanager.delete.debug-delay-sec keeps the container logs around for
  # a while, so dump them here (before the container is removed) when the tests
  # failed. Log aggregation isn't enabled, so we read the on-disk logs directly.
  if ! docker container inspect "$NAMENODE" >/dev/null 2>&1; then
    echo "YARN cluster container $NAMENODE was not started; no YARN logs to dump"
    return
  fi
  echo "===== YARN cluster container output ====="
  docker logs "$NAMENODE" 2>&1 || true
  echo "===== YARN daemon and application logs ====="
  docker exec "$NAMENODE" bash -c '
    find /usr/local/hadoop/logs /tmp/hadoop-root /var/log/hadoop-yarn \
      -type f \( \
        -name "*resourcemanager*" -o \
        -name "*nodemanager*" -o \
        -name "*.log" -o \
        -name syslog -o \
        -name stdout -o \
        -name stderr \
      \) -print0 2>/dev/null | sort -z | while IFS= read -r -d "" f; do
      echo "----- $f -----"
      cat "$f"
    done
  ' || true
}

cleanup() {
  STATUS=$?
  if [ "$STATUS" != 0 ]; then
    echo "Tests failed (exit code $STATUS), dumping YARN logs" 1>&2
    dump_yarn_logs
  fi
  echo "Cleaning-up container $NAMENODE"
  docker rm -f "$NAMENODE"
}

trap cleanup EXIT INT TERM


CACHE="${YARN_CACHE:-"$(pwd)/target/yarn"}"
HADOOP_CONF_CACHE="$CACHE/hadoop-conf-$HADOOP_MAJOR_VERSION"

mkdir -p "$CACHE"

if [ "$HADOOP_MAJOR_VERSION" = 3 ] && ! docker image inspect "$YARN_CLUSTER_IMAGE" >/dev/null 2>&1; then
  echo "Building Hadoop $HADOOP_3_VERSION YARN cluster image" 1>&2
  docker build --load -t "$YARN_CLUSTER_IMAGE" \
    --build-arg "HADOOP_VERSION=$HADOOP_3_VERSION" \
    --build-arg "JVM_VERSION=$JVM_VERSION" \
    - <<'EOF'
FROM docker.io/alexarchambault/yarn-cluster
ARG HADOOP_VERSION
ARG JVM_VERSION
ENV HADOOP_HOME=/usr/local/hadoop \
    HDFS_NAMENODE_USER=root \
    HDFS_DATANODE_USER=root \
    HDFS_SECONDARYNAMENODE_USER=root \
    YARN_RESOURCEMANAGER_USER=root \
    YARN_NODEMANAGER_USER=root

RUN cp -a /usr/local/hadoop/etc/hadoop /tmp/hadoop-conf && \
    curl -fsSL "https://dlcdn.apache.org/hadoop/common/hadoop-${HADOOP_VERSION}/hadoop-${HADOOP_VERSION}.tar.gz" \
      | tar -xz -C /usr/local && \
    rm /usr/local/hadoop && \
    ln -s "/usr/local/hadoop-${HADOOP_VERSION}" /usr/local/hadoop && \
    cp /tmp/hadoop-conf/*.xml /usr/local/hadoop/etc/hadoop/ && \
    printf '\nexport JAVA_HOME=/usr/java/default\n' >> /usr/local/hadoop/etc/hadoop/hadoop-env.sh && \
    chmod +x /usr/local/hadoop/etc/hadoop/*-env.sh && \
    rm -rf /tmp/hadoop-root/dfs/name && \
    /usr/local/hadoop/bin/hdfs namenode -format -force

RUN curl --retry 5 --retry-delay 2 -fL \
      -o /tmp/cs.gz \
      https://github.com/coursier/coursier/releases/download/v2.1.25-M26/cs-x86_64-pc-linux.gz && \
    gzip -dc /tmp/cs.gz > /usr/local/bin/cs && \
    rm -f /tmp/cs.gz && \
    chmod +x /usr/local/bin/cs && \
    cs java-home --jvm "$JVM_VERSION"

EOF
fi

if [ ! -x "$CACHE/coursier" ]; then
  COURSIER_GZ="$CACHE/coursier.gz"
  rm -f "$COURSIER_GZ"
  curl --retry 5 --retry-all-errors --retry-delay 2 -fL \
    -o "$COURSIER_GZ" \
    https://github.com/coursier/coursier/releases/download/v2.1.25-M25/cs-x86_64-pc-linux.gz
  gzip -dc "$COURSIER_GZ" > "$CACHE/coursier"
  rm -f "$COURSIER_GZ"
  chmod +x "$CACHE/coursier"
fi

cp mill "$CACHE/mill"
chmod +x "$CACHE/mill"

# ports allow to more easily access stuff from the outside
if [[ -z "$(docker ps -qf name=namenode)" ]]; then
  # extract the default core-site.xml from the image, so that we can tweak it
  # below (point it at the actual container IP rather than the "namenode" host)
  CONF_OVERRIDES_DIR="$(pwd)/target/conf-overrides"
  mkdir -p "$CONF_OVERRIDES_DIR"
  docker run --rm --entrypoint cat "$YARN_CLUSTER_IMAGE" /usr/local/hadoop/etc/hadoop/core-site.xml > "$CONF_OVERRIDES_DIR/core-site.xml"
  docker run --rm --entrypoint cat "$YARN_CLUSTER_IMAGE" /usr/local/hadoop/etc/hadoop/yarn-site.xml > "$CONF_OVERRIDES_DIR/yarn-site.xml"

  # start the container with a dummy command that never ends, mounting the
  # extracted core-site.xml so that we can edit it before bootstrapping
  docker run -d \
    -p 8088:8088 \
    -p 8042:8042 \
    --name "$NAMENODE" \
    -h "$NAMENODE" \
    -v "$CONF_OVERRIDES_DIR:/conf-overrides" \
    "$YARN_CLUSTER_IMAGE" tail -f /dev/null

  # point the conf at the actual container IP
  NAMENODE_IP="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$NAMENODE")"
  sed -i "s|hdfs://namenode|hdfs://$NAMENODE_IP|g" "$CONF_OVERRIDES_DIR/core-site.xml"
  sed -i "s|namenode:|$NAMENODE_IP:|g" "$CONF_OVERRIDES_DIR/yarn-site.xml"

  docker exec "$NAMENODE" bash -c "cp /conf-overrides/* /usr/local/hadoop/etc/hadoop/"

  # now actually bootstrap the namenode
  docker exec -d "$NAMENODE" /etc/bootstrap.sh -namenode -d
fi

echo "Waiting for namenode to be ready" 1>&2
RETRY=20
while [ "$RETRY" -gt 0 ] && ! docker exec -t "$NAMENODE" /usr/local/hadoop/bin/hdfs dfs -ls hdfs:///; do
  sleep 2
  RETRY=$(( $RETRY - 1 ))
done

if [ "$RETRY" = 0 ]; then
  echo "Timeout!"
  exit 1
fi

echo "Waiting for namenode to leave safe mode" 1>&2
RETRY=20
while [ "$RETRY" -gt 0 ] && ! docker exec -t "$NAMENODE" /usr/local/hadoop/bin/hdfs dfsadmin -safemode get | grep -w OFF; do
  sleep 2
  RETRY=$(( $RETRY - 1 ))
done

if [ "$RETRY" = 0 ]; then
  echo "Timeout!"
  exit 1
fi

echo "Querying Hadoop version from the YARN ResourceManager" 1>&2
RETRY=20
HADOOP_CLUSTER_VERSION=""
while [ "$RETRY" -gt 0 ] && [ -z "$HADOOP_CLUSTER_VERSION" ]; do
  CLUSTER_INFO="$(docker exec "$NAMENODE" curl -fsS http://localhost:8088/ws/v1/cluster/info || true)"
  HADOOP_CLUSTER_VERSION="$(
    echo "$CLUSTER_INFO" | sed -n 's/.*"hadoopVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
  )"
  if [ -z "$HADOOP_CLUSTER_VERSION" ]; then
    sleep 2
    RETRY=$(( RETRY - 1 ))
  fi
done

if [ -z "$HADOOP_CLUSTER_VERSION" ]; then
  echo "Could not query the Hadoop version from the YARN ResourceManager" 1>&2
  exit 1
fi
echo "YARN ResourceManager cluster info:"
echo "$CLUSTER_INFO"
echo "Hadoop cluster version: $HADOOP_CLUSTER_VERSION"
export HADOOP_CLUSTER_VERSION


export INPUT_TXT_URL="hdfs:///user/root/input.txt"

if ! docker exec -t "$NAMENODE" /usr/local/hadoop/bin/hdfs dfs -ls hdfs:///user/root/input.txt; then
  echo "Copying file to $INPUT_TXT_URL"
  (docker exec -i "$NAMENODE" /usr/local/hadoop/bin/hdfs dfs -put - "$INPUT_TXT_URL") < modules/test-definitions/resources/input.txt
fi

if [ ! -d "$HADOOP_CONF_CACHE" ]; then
  echo "Getting Hadoop conf dir"
  mkdir -p "$HADOOP_CONF_CACHE"
  docker exec "$NAMENODE" tar -C /usr/local/hadoop/etc/hadoop -cf - . | tar -C "$HADOOP_CONF_CACHE" -xf -
fi

echo cat "$HADOOP_CONF_CACHE/core-site.xml"
cat "$HADOOP_CONF_CACHE/core-site.xml"
echo

echo cat "$HADOOP_CONF_CACHE/yarn-site.xml"
cat "$HADOOP_CONF_CACHE/yarn-site.xml"
echo

SPARK_YARN_JAVA_HOME=""
if [ "$HADOOP_MAJOR_VERSION" = 3 ]; then
  SPARK_YARN_JAVA_HOME="$(docker exec "$NAMENODE" cs java-home --jvm "$JVM_VERSION")"
  echo "Spark YARN Java home: $SPARK_YARN_JAVA_HOME"
fi

cat > "$CACHE/run.sh" << EOF
#!/usr/bin/env bash
set -e

EOF

if [ -n "$PREFETCH_SPARK_VERSION" ]; then
  SBV="$(echo "$SCALA_VERSION" | cut -d . -f 1-2)"
  cat >> "$CACHE/run.sh" << EOF
DEPS=()
DEPS+=("org.apache.spark:spark-sql_$SBV:$PREFETCH_SPARK_VERSION")
DEPS+=("org.apache.spark:spark-yarn_$SBV:$PREFETCH_SPARK_VERSION")

for d in "\${DEPS[@]}"; do
  echo "Pre-fetching \$d"
  coursier fetch "\$d" $(if [ "$INTERACTIVE" = 1 ]; then echo --progress; else echo "</dev/null"; fi) >/dev/null
done

EOF
fi

cat >> "$CACHE/run.sh" << EOF
$(if [ "$INTERACTIVE" = 0 ]; then echo "export CI=true"; fi)

cat > .mill-jvm-opts << FOO
-Xmx1g
FOO

eval "\$(coursier java --env --jvm $JVM_VERSION)"
$(if [ -n "$SPARK_YARN_JAVA_HOME" ]; then echo "export SPARK_YARN_JAVA_HOME=$SPARK_YARN_JAVA_HOME"; fi)

export HADOOP_CONF_DIR=/etc/hadoop/conf
export YARN_CONF_DIR=/etc/hadoop/conf

apt-get update
apt-get install -y curl

# In yarn-client mode the ApplicationMaster (running in the YARN cluster
# container) connects back to the driver. Advertise this container's bridge IP
# as spark.driver.host - otherwise the AM resolves the driver's container
# hostname, which it can't reach, and dies right after registering with the RM.
export SPARK_DRIVER_HOST="\$(getent hosts "\$(hostname)" | awk '{print \$1; exit}')"
echo "SPARK_DRIVER_HOST=\$SPARK_DRIVER_HOST"

export AMMONITE_SPARK_FORCED_VERSION="0.1-SNAPSHOT"
export AMMONITE_SPARK_FORCED_COMMIT_HASH="XXXX"

echo "Hadoop cluster version (from ResourceManager): \$HADOOP_CLUSTER_VERSION"
echo exec ./mill -i "\$@"
exec ./mill -i "\$@"
EOF

chmod +x "$CACHE/run.sh"

docker run -t $(if [ "$INTERACTIVE" = 1 ]; then echo -i; fi) --rm \
  --name ammonite-spark-its \
  -p 4040:4040 \
  -v "$CACHE/coursier:/usr/local/bin/coursier" \
  -v "$CACHE/mill:/usr/local/bin/mill" \
  -v "$CACHE/run.sh:/usr/local/bin/run.sh" \
  -v "$CACHE/cache:/root/.cache" \
  -v "$CACHE/mill-home:/root/.mill" \
  -v "$CACHE/ivy2-home:/root/.ivy2" \
  -v "$HADOOP_CONF_CACHE:/etc/hadoop/conf" \
  -v "$(pwd):/workspace" \
  $(if [ ! -z ${SPARK_LOG_CONSOLE+x} ]; then echo "" -e SPARK_LOG_CONSOLE; fi) \
  -e HADOOP_CLUSTER_VERSION \
  -e INPUT_TXT_URL \
  -w /workspace \
  ubuntu:26.04 \
   /usr/local/bin/run.sh "$@"
