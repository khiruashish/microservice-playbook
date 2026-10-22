#!/usr/bin/env bash
# Chapter 66's experiments: pinning on JDK 21 vs 25, fan-out tail latency and hedging, and the quote endpoint in four
# concurrency models under open load. About 10 minutes. Needs a JDK 25 (JAVA_HOME) and a JDK 21 (JDK21_HOME).
#   ./run.sh                     everything, then the tests and the API checks
#   ./run.sh pinning tail models
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}" "${JDK21_HOME:?set JDK21_HOME to a JDK 21}"
SRC=${C66_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 21 -nowarn -d build/main $(find ../src/main/java -name '*.java')   # Java 21, so JDK 21 runs it too
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JDK21_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc66.classes=build/main -Dc66.jdk21="$JDK21_HOME/bin/java" Lab ${*:-pinning tail models}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
