#!/usr/bin/env bash
set -e

# when the tests are running, open the YARN UI at http://localhost:8088

INTERACTIVE=1
if [ "$1" = -batch ]; then
  INTERACTIVE=0
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
  curl -Lo "$CACHE/coursier" https://github.com/coursier/coursier/raw/v1.1.0-M6/coursier
  chmod +x "$CACHE/coursier"
fi

if [ ! -x "$CACHE/sbt" ]; then
  curl -Lo "$CACHE/sbt" https://github.com/paulp/sbt-extras/raw/65a871dc720c18614a0d8d0db6b52d25ed98dffb/sbt
  chmod +x "$CACHE/sbt"
fi

if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  docker network create "$NETWORK"
fi

# ports allow to more easily access stuff from the outside
docker run -d \
  -p 8088:8088 \
  -p 8042:8042 \
  --name "$NAMENODE" \
  -h "$NAMENODE" \
  --network "$NETWORK" \
  alexarchambault/yarn-cluster /etc/bootstrap.sh -namenode -d

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

echo "Copying file to $INPUT_TXT_URL"
(docker exec -i "$NAMENODE" /usr/local/hadoop/bin/hdfs dfs -put - "$INPUT_TXT_URL") < modules/tests/src/main/resources/input.txt

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

cat > "$CACHE/run.sh" << EOF
#!/usr/bin/env bash
set -e

# prefetch stuff
for d in org.apache.spark:spark-sql_2.11:2.3.1 org.apache.spark:spark-yarn_2.11:2.3.1; do
  echo "Pre-fetching \$d"
  coursier fetch $(if [ "$INTERACTIVE" = 1 ]; then echo --progress; fi) "\$d" >/dev/null
done

exec sbt -J-Xmx1g "\$@"
EOF

chmod +x "$CACHE/run.sh"

docker run -t $(if [ "$INTERACTIVE" = 1 ]; then echo -i; fi) --rm \
  --name ammonite-spark-its \
  --network "$NETWORK" \
  -p 4040:4040 \
  -v "$CACHE/coursier:/usr/local/bin/coursier" \
  -v "$CACHE/sbt:/usr/local/bin/sbt" \
  -v "$CACHE/run.sh:/usr/local/bin/run.sh" \
  -v "$CACHE/cache:/root/.cache" \
  -v "$CACHE/sbt-home:/root/.sbt" \
  -v "$CACHE/ivy2-home:/root/.ivy2" \
  -v "$CACHE/hadoop-conf:/etc/hadoop/conf" \
  -v "$(pwd):/workspace" \
  -e INPUT_TXT_URL \
  -w /workspace \
  openjdk:8u151-jdk \
   /usr/local/bin/run.sh "$@"
