#!/usr/bin/env bash
# Chapter 68's experiments: sessions in memory or shared, with and without affinity; how a Service spreads long-lived
# connections; throughput with more copies, alone and behind a shared dependency; and partitioning strategies. About
# 15 minutes. Needs docker, a JDK 25 (JAVA_HOME) and a cluster: ../../ch52-health-checks-actuator/verify/k3s-lab.sh
# starts one, or set KUBECTL and LOAD_IMAGE for your own. The load runs from this machine to the Service's cluster IP.
#   ./run.sh                     everything, then the tests and the API checks
#   ./run.sh sessions connections scaleout partitioning
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C68_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
./build-libs.sh
L=build/lib
rm -rf build/test build/tools && mkdir -p build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
# Connection: close needs the JDK client's permission to set a restricted header
"$JAVA_HOME/bin/java" -Djdk.httpclient.allowRestrictedHeaders=connection -cp "build/main:build/tools:$L/*" -Dc68.kubectl="$KUBECTL" \
  -Dc68.manifest=../src/main/resources/k8s/scaling.yaml Lab ${*:-sessions connections scaleout partitioning}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
