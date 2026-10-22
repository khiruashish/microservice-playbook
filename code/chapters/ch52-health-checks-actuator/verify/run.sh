#!/usr/bin/env bash
# Chapter 52 measurements on a real Kubernetes cluster: probes, Service endpoints and rolling updates are Kubernetes'
# own. About 30 minutes. Needs docker, a JDK 21+, PostgreSQL's server binaries (PG_BIN, run as user postgres) and a
# cluster: ./k3s-lab.sh starts one, or set KUBECTL (and LOAD_IMAGE, e.g. "kind load docker-image") for your own.
#   ./run.sh                                  everything, then the API checks
#   ./run.sh dependency startup shutdown actuator
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
JBIN=${JAVA_HOME:+$JAVA_HOME/bin/}
SRC=${C52_SOURCES:-build/src}
KUBECTL=${KUBECTL:-build/k3s/k3s kubectl}
LOAD_IMAGE=${LOAD_IMAGE:-build/k3s/k3s ctr images import -}
PG_BIN=${PG_BIN:-/usr/lib/postgresql/16/bin}
./build-libs.sh

rm -rf build/main build/tools && mkdir -p build/main build/tools
${JBIN}javac --release 21 -nowarn -d build/main $(find ../src/main/java -name '*.java')
${JBIN}javac --release 21 -nowarn -d build/tools -cp build/main tools/*.java
# the order-service image: the lab service on the jlink'd runtime
rm -rf build/image && mkdir -p build/image && cp -r build/main build/tools build/image/
printf 'FROM ff-jre:25\nCOPY main /app/main\nCOPY tools /app/tools\nENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app/main:/app/tools", "OrderService"]\n' > build/image/Dockerfile
docker build -q -t foodfleet/order-service:ch52 build/image > /dev/null
if [[ "$LOAD_IMAGE" == *" -" ]]; then docker save foodfleet/order-service:ch52 | ${LOAD_IMAGE} > /dev/null 2>&1
else ${LOAD_IMAGE} foodfleet/order-service:ch52 > /dev/null; fi

NODE=$($KUBECTL get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null)
# a lab PostgreSQL outside the cluster, like a managed database, reachable from pods on the node's address
PGDATA=/tmp/c52-pgdata
if [ ! -d $PGDATA ]; then
  mkdir -p $PGDATA && chown postgres $PGDATA
  su postgres -s /bin/bash -c "$PG_BIN/initdb -D $PGDATA/data -A trust -U postgres > /dev/null"
  echo "listen_addresses='*'" >> $PGDATA/data/postgresql.conf
  echo "host all all 0.0.0.0/0 trust" >> $PGDATA/data/pg_hba.conf    # lab only: trust, no password
fi
su postgres -s /bin/bash -c "$PG_BIN/pg_ctl -D $PGDATA/data status > /dev/null || $PG_BIN/pg_ctl -D $PGDATA/data -l $PGDATA/log -w start > /dev/null"
STOP="su postgres -s /bin/bash -c '$PG_BIN/pg_ctl -D $PGDATA/data -m fast -w stop'"
START="su postgres -s /bin/bash -c '$PG_BIN/pg_ctl -D $PGDATA/data -l $PGDATA/log -w start'"

${JBIN}java -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
MODES=${*:-dependency startup shutdown actuator}
for m in $MODES; do
  echo "== $m"
  ${JBIN}java -cp build/main:build/tools -Dc52.node="$NODE" -Dc52.kubectl="$KUBECTL" -Dc52.dbStop="$STOP" -Dc52.dbStart="$START" \
    -Dc52.java="${JBIN}java" -Dc52.cp="$PWD/build/main:$PWD/build/tools" Measure "$m" 2>&1 | grep -v DEPRECATION
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
