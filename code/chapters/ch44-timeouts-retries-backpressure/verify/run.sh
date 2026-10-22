#!/usr/bin/env bash
# Chapter 44 measurements against Netflix concurrency-limits 0.5.4, Resilience4j 2.4.0's Hedge and the JDK's Flow API.
# Needs git and a JDK (the chapter's numbers: JDK 25.0.4). About 14 minutes in all.
#   ./run.sh                                   every experiment, then the API checks
#   ./run.sh tail limits priority brownout streams
#   JAVA_HOME=/path/to/jdk-21 ./run.sh priority    the FIFO-wait row on JDK 21 (virtual threads pin in synchronized)
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
export JAVAC=${BIN}javac
./build-libs.sh > /dev/null
LIBS=build/lib/concurrency-limits-core-0.5.4.jar:build/lib/resilience4j-2.4.0.jar
rm -rf build/main build/tools && mkdir -p build/main build/tools
$JAVAC --release 21 -d build/main -cp $LIBS $(find ../src/main/java -name '*.java' -not -path '*/web/*')
$JAVAC --release 21 -d build/tools -cp build/main:$LIBS tools/*.java
${BIN}java -version 2>&1 | head -1
MODES=${*:-tail limits priority brownout streams}
for m in $MODES; do
  echo "== $m"
  ${BIN}java -Xmx1g -Dslf4j.internal.verbosity=ERROR -cp build/main:build/tools:$LIBS Measure "$m"
  echo
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "${C44_SOURCES:-build/src}" "${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
