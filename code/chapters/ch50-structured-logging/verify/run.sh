#!/usr/bin/env bash
# Chapter 50 measurements on Logback 1.5.38 / SLF4J 2.0.18 and the OpenTelemetry Java agent 2.32.0. About 4 minutes.
# Needs git, curl, python3 and a JDK 21+.
#   ./run.sh                          everything, then the API checks
#   ./run.sh mdc async cost masking
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
JBIN=${JAVA_HOME:+$JAVA_HOME/bin/}
export JAVAC=${JBIN}javac
./build-libs.sh > /dev/null
SRC=${C50_SOURCES:-build/src}
rm -rf build/main build/tools && mkdir -p build/main build/tools
# MdcTaskDecorator needs Spring's TaskDecorator interface to compile; it comes from the source tree, not a jar
$JAVAC --release 21 -nowarn -implicit:none -d build/main -cp build/lib/logback \
  -sourcepath "$SRC/sf/spring-core/src/main/java:$SRC/jspecify/src/main/java" $(find ../src/main/java -name '*.java')
$JAVAC --release 21 -nowarn -d build/tools -cp build/main:build/lib/logback tools/*.java
CP="$PWD/build/main:$PWD/build/tools:$PWD/build/lib/logback"
${JBIN}java -version 2>&1 | head -1
MODES=${*:-mdc async cost masking}
for m in $MODES; do
  echo "== $m"
  ${JBIN}java -Xmx2g -Dc50.agent="$PWD/build/bin/opentelemetry-javaagent.jar" -Dc50.cp="$CP" -cp "$CP" Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
