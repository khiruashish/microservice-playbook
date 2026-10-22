#!/usr/bin/env bash
# Chapter 65's experiments: load models against a stalling service and towards capacity, a chaos experiment with
# three client configurations, and architecture fitness functions against planted violations. About 10 minutes.
# Needs a JDK 25 (JAVA_HOME).
#   ./run.sh                                 everything, then the tests and the API checks
#   ./run.sh stall capacity chaos arch
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C65_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools build/work && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools Lab ../src/main/java/dev/foodfleet/ch65/sample violations.tsv build/work ${*:-stall capacity chaos arch}
if [ -d build/work/V8 ]; then
  echo "jdeps on the classes with V8 (an inlined constant) and V10 (Class.forName):"
  for v in V8 V10; do echo "  $v:"; "$JAVA_HOME/bin/jdeps" -verbose:class build/work/$v/classes | grep 'domain.order.Order .*infrastructure' || echo "    no dependency from domain on infrastructure"; done
fi
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit arch
echo "== apis"
./check_apis.sh "$SRC"
