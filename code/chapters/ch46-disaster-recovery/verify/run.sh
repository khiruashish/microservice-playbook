#!/usr/bin/env bash
# Chapter 46 measurements on real PostgreSQL clusters: RPO by replication mode and distance, failover timelines,
# backup-and-restore RPO and restore times, and cell/shuffle-shard blast radius. About 12 minutes.
# Needs git, curl, a JDK (the chapter's numbers: JDK 25.0.4) and PostgreSQL 16 server binaries (PG_BIN, or pg_config
# on the PATH). Run as an ordinary user, or as root (PostgreSQL commands then run as the postgres user).
#   ./run.sh                          everything, then the API checks
#   ./run.sh rpo rto restore cells
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
./build-libs.sh > /dev/null
export PG_BIN=${PG_BIN:-$(pg_config --bindir)}
export TOXIPROXY_BIN=$PWD/build/bin/toxiproxy-server
rm -rf build/main build/tools && mkdir -p build/main build/tools
${BIN}javac --release 21 -d build/main $(find ../src/main/java -name '*.java')
${BIN}javac --release 21 -d build/tools -cp build/main tools/*.java
${BIN}java -version 2>&1 | head -1
"$PG_BIN/postgres" --version
MODES=${*:-rpo rto restore cells}
for m in $MODES; do
  echo "== $m"
  ${BIN}java -Xmx1g -Dc46.dir=${C46_DIR:-/tmp/c46pg} -cp build/main:build/tools Measure "$m"
  echo
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "${C46_SOURCES:-build/src}"
