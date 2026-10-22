#!/usr/bin/env bash
# Chapter 70's experiments: three workloads through a compressed day against their declared requests, VPA-style
# recommendations and the api at the recommended size (Docker); the fleet packed onto nodes and divided among teams
# (calculated from those measurements); and spot copies reclaimed under load (a cluster). About 30 minutes. Needs a
# JDK 25 (JAVA_HOME), Docker and a cluster: ../../ch52-health-checks-actuator/verify/k3s-lab.sh starts one.
# Containers mount the JDK into a minimal image (C70_IMAGE, default ff-jre:25: any image with glibc works).
#   ./run.sh                     everything, then the tests and the API checks
#   ./run.sh usage fleet spot    (fleet needs usage in the same run)
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C70_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
./build-libs.sh
L=build/lib
rm -rf build/test build/tools && mkdir -p build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -Djdk.httpclient.allowRestrictedHeaders=connection -cp "build/main:build/tools" -Dc70.jdk="$(cd "$JAVA_HOME" && pwd -P)" \
  -Dc70.classes="$(pwd)/build/main" -Dc70.image="${C70_IMAGE:-ff-jre:25}" -Dc70.fleet=fleet.tsv -Dc70.kubectl="$KUBECTL" \
  -Dc70.manifest=../src/main/resources/k8s/menu.yaml Lab ${*:-usage fleet spot}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
