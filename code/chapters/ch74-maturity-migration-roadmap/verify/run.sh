#!/usr/bin/env bash
# Chapter 74's experiments on Broadleaf Commerce (broadleaf-7.1.0-GA, ten years of history): coupling between its
# bounded contexts and the fewest edges that stop them forming layers; how its commits spread over contexts; and five
# extraction roadmaps, step by step. Under a minute after the clone. Needs a JDK 25 (JAVA_HOME) and git.
#   ./run.sh                          everything, then the tests and the API checks
#   ./run.sh coupling history roadmap
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C74_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc74.repo="$SRC/broadleaf" Lab ${*:-coupling history roadmap}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
