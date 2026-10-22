#!/usr/bin/env bash
# Chapter 67's experiments: JVM ergonomics in containers of different sizes, the collectors under the same load, the heap
# as a share of the container, native memory, and the cost of Flight Recorder. About 15 minutes. Needs a JDK 25
# (JAVA_HOME) and Docker; every JVM runs in a container with the JDK mounted read-only into a minimal image
# (C67_IMAGE, default ff-jre:25: any image with glibc works, such as gcr.io/distroless/base-debian12).
#   ./run.sh                     everything, then the tests and the API checks
#   ./run.sh ergonomics gc heap nmt jfr
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C67_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/tools -Dc67.jdk="$(cd "$JAVA_HOME" && pwd -P)" -Dc67.classes="$(pwd)/build/main" -Dc67.image="${C67_IMAGE:-ff-jre:25}" \
  Lab ${*:-ergonomics gc heap nmt jfr}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
