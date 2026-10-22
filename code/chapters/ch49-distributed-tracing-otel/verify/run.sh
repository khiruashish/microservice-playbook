#!/usr/bin/env bash
# Chapter 49 measurements: FoodFleet's checkout under the OpenTelemetry Java agent 2.32.0 with a NATS 2.15.1 broker
# and an OpenTelemetry Collector 0.162.0. About 12 minutes. Needs git, curl, Linux x86-64 and a JDK 21+.
#   ./run.sh                                       everything, then the API checks
#   ./run.sh context messaging sampling baggage
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
JBIN=${JAVA_HOME:+$JAVA_HOME/bin/}
export JAVAC=${JBIN}javac
./build-libs.sh > /dev/null
SRC=${C49_SOURCES:-build/src}
LIBS=build/lib/otel:build/lib/jnats
rm -rf build/main build/tools && mkdir -p build/main build/tools
$JAVAC --release 21 -nowarn -d build/main -cp build/lib/otel $(find ../src/main/java -name '*.java')
$JAVAC --release 21 -nowarn -d build/tools -cp build/main:$LIBS tools/*.java
CP="$PWD/build/main:$PWD/build/tools:$PWD/build/lib/otel:$PWD/build/lib/jnats"
${JBIN}java -version 2>&1 | head -1
MODES=${*:-context messaging sampling baggage}
for m in $MODES; do
  echo "== $m"
  ${JBIN}java -Xmx1g -Dc49.bin=build/bin -Dc49.cp="$CP" -cp "$CP" Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
