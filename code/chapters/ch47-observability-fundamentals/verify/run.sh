#!/usr/bin/env bash
# Chapter 47 measurements: FoodFleet's checkout services as real JVMs instrumented by the OpenTelemetry Java agent
# 2.32.0, an OpenTelemetry Collector Contrib 0.162.0, Prometheus 3.15.0 and JDK Flight Recorder. About 16 minutes.
# Needs git, curl, Linux x86-64 and a JDK 21+ (the chapter's numbers: JDK 25.0.4, two vCPUs, 8 GB).
#   ./run.sh                                   everything, then the configuration and API checks
#   ./run.sh find overhead profile cardinality
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
JBIN=${JAVA_HOME:+$JAVA_HOME/bin/}
./build-libs.sh > /dev/null
SRC=${C47_SOURCES:-build/src}
rm -rf build/main build/tools && mkdir -p build/main build/tools
${JBIN}javac --release 21 -d build/main $(find ../src/main/java -name '*.java')
${JBIN}javac --release 21 -d build/tools -cp build/main tools/*.java
${JBIN}java -version 2>&1 | head -1
MODES=${*:-find overhead profile cardinality}
for m in $MODES; do
  echo "== $m"
  ${JBIN}java -Xmx1g -Dc47.bin=build/bin -Dc47.cp="$PWD/build/main:$PWD/build/tools" -cp build/main:build/tools Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== configs"
build/bin/otelcol-contrib validate --config=../src/main/resources/otel/collector.yaml && echo "OK       otel/collector.yaml is a valid otelcol-contrib 0.162.0 configuration"
build/bin/promtool check config ../src/main/resources/prometheus/prometheus.yml
echo
echo "== apis"
./check_apis.sh "$SRC"
