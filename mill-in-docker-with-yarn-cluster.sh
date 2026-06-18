#!/usr/bin/env bash
set -eu

# when the tests are running, open the YARN UI at http://localhost:8088

PREFETCH=0
if [ "$1" == "--prefetch" ]; then
  PREFETCH=1
  shift
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
  echo "===== ResourceManager log ====="
  docker exec "$NAMENODE" bash -c 'cat /usr/local/hadoop/logs/yarn-*-resourcemanager-*.log' || true
  echo "===== YARN container logs (AM + executors) ====="
  docker exec "$NAMENODE" bash -c '
    for f in $(find /usr/local/hadoop/logs/userlogs -type f | sort); do
      echo "----- $f -----"
      cat "$f"
    done' || true
  # The container console (stdout/stderr above) doesn't always carry the AM's
  # log4j output - e.g. when it logs to a file. The container working dirs (kept
  # around by debug-delay-sec) hold those *.log files, which contain the actual
  # AM exception.
  echo "===== YARN container working-dir logs (*.log) ====="
  docker exec "$NAMENODE" bash -c '
    for f in $(find /tmp/hadoop-root/nm-local-dir -name "*.log" | sort); do
      echo "----- $f -----"
      cat "$f"
    done' || true
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

mkdir -p "$CACHE"

if [ ! -x "$CACHE/coursier" ]; then
  curl -fL https://github.com/coursier/coursier/releases/download/v2.1.25-M25/cs-x86_64-pc-linux.gz | gzip -d > "$CACHE/coursier"
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
  docker run --rm --entrypoint cat alexarchambault/yarn-cluster /usr/local/hadoop/etc/hadoop/core-site.xml > "$CONF_OVERRIDES_DIR/core-site.xml"
  docker run --rm --entrypoint cat alexarchambault/yarn-cluster /usr/local/hadoop/etc/hadoop/yarn-site.xml > "$CONF_OVERRIDES_DIR/yarn-site.xml"

  # start the container with a dummy command that never ends, mounting the
  # extracted core-site.xml so that we can edit it before bootstrapping
  docker run -d \
    -p 8088:8088 \
    -p 8042:8042 \
    --name "$NAMENODE" \
    -h "$NAMENODE" \
    -v "$CONF_OVERRIDES_DIR:/conf-overrides" \
    docker.io/alexarchambault/yarn-cluster tail -f /dev/null

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


export INPUT_TXT_URL="hdfs:///user/root/input.txt"

if ! docker exec -t "$NAMENODE" /usr/local/hadoop/bin/hdfs dfs -ls hdfs:///user/root/input.txt; then
  echo "Copying file to $INPUT_TXT_URL"
  (docker exec -i "$NAMENODE" /usr/local/hadoop/bin/hdfs dfs -put - "$INPUT_TXT_URL") < modules/test-definitions/resources/input.txt
fi

if [ ! -d "$CACHE/hadoop-conf" ]; then
  echo "Getting Hadoop conf dir"
  mkdir -p "$CACHE/hadoop-conf"
  docker exec "$NAMENODE" tar -C /usr/local/hadoop/etc/hadoop -cf - . | tar -C "$CACHE/hadoop-conf" -xf -
fi

echo cat "$CACHE/hadoop-conf/core-site.xml"
cat "$CACHE/hadoop-conf/core-site.xml"
echo

echo cat "$CACHE/hadoop-conf/yarn-site.xml"
cat "$CACHE/hadoop-conf/yarn-site.xml"
echo

SCALA_VERSION="2.12.8"
SBV="2.12"

cat > "$CACHE/run.sh" << EOF
#!/usr/bin/env bash
set -e

EOF

if [ "$PREFETCH" == 1 ]; then
  cat >> "$CACHE/run.sh" << EOF
for SPARK_VERSION in "2.4.4" "3.0.0"; do
  DEPS=()
  DEPS+=("org.apache.spark:spark-sql_$SBV:\$SPARK_VERSION")
  DEPS+=("org.apache.spark:spark-yarn_$SBV:\$SPARK_VERSION")

  for d in "\${DEPS[@]}"; do
    echo "Pre-fetching \$d"
    coursier fetch "\$d" $(if [ "$INTERACTIVE" = 1 ]; then echo --progress; else echo "</dev/null"; fi) >/dev/null
  done
done

EOF
fi

cat >> "$CACHE/run.sh" << EOF
$(if [ "$INTERACTIVE" = 0 ]; then echo "export CI=true"; fi)

cat > .mill-jvm-opts << FOO
-Xmx1g
FOO

eval "\$(coursier java --env --jvm 11)"

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
  -v "$CACHE/hadoop-conf:/etc/hadoop/conf" \
  -v "$(pwd):/workspace" \
  $(if [ ! -z ${SPARK_LOG_CONSOLE+x} ]; then echo "" -e SPARK_LOG_CONSOLE; fi) \
  -e INPUT_TXT_URL \
  -w /workspace \
  ubuntu:26.04 \
   /usr/local/bin/run.sh "$@"
