#!/usr/bin/env bash
# Chapter 72's experiments: a new service from the golden path to a running pod; twelve services that drifted, against
# the platform's conformance checks and the API server; and the v1 services upgraded to template v2 by three-way merge.
# About 2 minutes. Needs a JDK 25 (JAVA_HOME), docker, git and a cluster: ../../ch52-health-checks-actuator/verify/k3s-lab.sh
# starts one, or set KUBECTL and LOAD_IMAGE for your own.
#   ./run.sh                     everything, then the tests and the API checks
#   ./run.sh scaffold fleet upgrade
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C72_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
LOAD_IMAGE=${LOAD_IMAGE:-$(pwd)/../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc72.templates=../src/main/resources/templates -Dc72.work=build/work -Dc72.kubectl="$KUBECTL" \
  -Dc72.loadImage="$LOAD_IMAGE" Lab ${*:-scaffold fleet upgrade}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -Dc72.templates=../src/main/resources/templates -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
