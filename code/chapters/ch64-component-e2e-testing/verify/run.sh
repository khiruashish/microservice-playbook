#!/usr/bin/env bash
# Chapter 64's experiments on a cluster: test environments per namespace, shared staging vs fresh environments,
# sleeps vs polling, component tests vs E2E. About 15 minutes. Needs docker, a JDK 25 (JAVA_HOME) and a cluster:
# ../../ch52-health-checks-actuator/verify/k3s-lab.sh starts one, or set KUBECTL and LOAD_IMAGE for your own.
#   ./run.sh                                     everything, then the tests and the API checks
#   ./run.sh environments staging waits component
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C64_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
./build-libs.sh
L=build/lib
rm -rf build/test build/tools && mkdir -p build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
"$JAVA_HOME/bin/java" -Duser.timezone=UTC -cp "build/main:build/tools:$L/*" -Dc64.kubectl="$KUBECTL" -Dc64.manifest=../src/main/resources/k8s/environment.yaml \
  Lab ${*:-environments staging waits component}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -Duser.timezone=UTC -cp "build/main:build/test:build/tools:$L/*" RunSuites unit component
echo "== apis"
./check_apis.sh "$SRC"
