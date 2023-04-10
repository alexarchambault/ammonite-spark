#!/usr/bin/env bash
set -eu

# when the tests are running, open the YARN UI at http://localhost:8088

INTERACTIVE=0
if [ -t 1 ]; then
  INTERACTIVE=1
  shift
fi

cd "$(dirname "${BASH_SOURCE[0]}")"


NETWORK=ammonite-spark-yarn

# this name can't be changed (hardcoded in stuff in the yarn-cluster image)
NAMENODE=namenode


cleanup() {
  docker rm -f "$NAMENODE"
  docker network rm "$NETWORK" || true
}

trap cleanup EXIT INT TERM


CACHE="${YARN_CACHE:-"$(pwd)/target/yarn"}"

mkdir -p "$CACHE"

if [ ! -x "$CACHE/coursier" ]; then
  curl -Lo "$CACHE/coursier" https://github.com/coursier/coursier/raw/v2.0.0-RC2-6/coursier
  chmod +x "$CACHE/coursier"
fi

cp mill "$CACHE/mill"
chmod +x "$CACHE/mill"

if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  docker network create "$NETWORK"
fi

# ports allow to more easily access stuff from the outside
if [[ -z "$(docker ps -qf name=namenode)" ]]; then
  docker run -d \
    -p 8088:8088 \
    -p 8042:8042 \
    --name "$NAMENODE" \
    -h "$NAMENODE" \
    --network "$NETWORK" \
    alexarchambault/yarn-cluster /etc/bootstrap.sh -namenode -d
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
  (docker exec -i "$NAMENODE" /usr/local/hadoop/bin/hdfs dfs -put - "$INPUT_TXT_URL") < modules/tests/src/main/resources/input.txt
fi

if [ ! -d "$CACHE/hadoop-conf" ]; then
  echo "Getting Hadoop conf dir"
  # Ideally, we should get that conf from the running namenode container
  TRANSIENT_DOCKER_YARN_CLUSTER=0
  if [ ! -d "$CACHE/docker-yarn-cluster" ]; then
    TRANSIENT_DOCKER_YARN_CLUSTER=1
    git clone https://github.com/alexarchambault/docker-yarn-cluster.git "$CACHE/docker-yarn-cluster"
    cd "$CACHE/docker-yarn-cluster"
    git checkout 46d76004c4731a0fbf1b8025abede8a5ce0e843c
    cd -
  fi
  cp -R "$CACHE/docker-yarn-cluster/etc/hadoop" "$CACHE/hadoop-conf"
  test "$TRANSIENT_DOCKER_YARN_CLUSTER" = 0 || rm -rf "$CACHE/docker-yarn-cluster"
fi

SCALA_VERSION="${TRAVIS_SCALA_VERSION:-"2.12.8"}"
case "$SCALA_VERSION" in
  2.12.*)
    SBV="2.12"
    ;;
  *)
    echo "Unrecognized scala version: $SCALA_VERSION"
    exit 1
    ;;
esac

cat > "$CACHE/run.sh" << EOF
#!/usr/bin/env bash
set -e

if [ "\$SPARK_HOME" = "" ]; then
  # prefetch stuff

  for SPARK_VERSION in "2.4.4" "3.0.0"; do

    DEPS=()
    DEPS+=("org.apache.spark:spark-sql_$SBV:\$SPARK_VERSION")
    DEPS+=("org.apache.spark:spark-yarn_$SBV:\$SPARK_VERSION")

    for d in "\${DEPS[@]}"; do
      echo "Pre-fetching \$d"
      coursier fetch "\$d" $(if [ "$INTERACTIVE" = 1 ]; then echo --progress; else echo "</dev/null"; fi) >/dev/null
    done
  done
fi

$(if [ "$INTERACTIVE" = 0 ]; then echo "export CI=true"; fi)

cat > .mill-jvm-opts << FOO
-Xmx1g
FOO

exec mill -i "\$@"
EOF

chmod +x "$CACHE/run.sh"

docker run -t $(if [ "$INTERACTIVE" = 1 ]; then echo -i; fi) --rm \
  --name ammonite-spark-its \
  --network "$NETWORK" \
  -p 4040:4040 \
  -v "$CACHE/coursier:/usr/local/bin/coursier" \
  -v "$CACHE/mill:/usr/local/bin/mill" \
  -v "$CACHE/run.sh:/usr/local/bin/run.sh" \
  -v "$CACHE/cache:/root/.cache" \
  -v "$CACHE/mill-home:/root/.mill" \
  -v "$CACHE/ivy2-home:/root/.ivy2" \
  -v "$CACHE/hadoop-conf:/etc/hadoop/conf" \
  -v "$(pwd):/workspace" \
  $(if [ ! -z ${SPARK_HOME+x} ]; then echo "" -e SPARK_HOME=/spark -v "$SPARK_HOME:/spark"; fi) \
  $(if [ ! -z ${SPARK_VERSION+x} ]; then echo "" -e SPARK_VERSION; fi) \
  -e INPUT_TXT_URL \
  -w /workspace \
  openjdk:8u151-jdk \
   /usr/local/bin/run.sh "$@"
