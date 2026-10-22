#!/usr/bin/env bash
# Chapter 76's experiments: two functions cold-started in their own JVMs in four configurations (JVM, C1 + Serial GC, a
# JDK 25 AOT cache, both); a simulated day of four workloads on functions, scale-to-zero containers and always-on
# containers, driven by those measurements and AWS list prices; and the break-even rate. About 2 minutes.
# Needs a JDK 25 (JAVA_HOME) and git.
#   ./run.sh                       everything, then the tests and the API checks
#   ./run.sh starts day breakeven
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C76_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools build/work && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
# the AOT cache needs the application on JARs: class directories on the class path are refused when it's written
"$JAVA_HOME/bin/jar" --create --file build/functions.jar -C build/main .
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc76.classes=build/functions.jar Lab ${*:-starts day breakeven}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
