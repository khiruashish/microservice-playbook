#!/usr/bin/env bash
# Chapter 71's experiments: linting six OpenAPI documents with planted problems, a ratchet on a legacy document,
# deprecation headers and usage, and governance checks over the Backstage catalog. Under a minute. Needs a JDK 25.
#   ./run.sh                     everything, then the tests and the API checks
#   ./run.sh lint ratchet deprecation catalog
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C71_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc71.resources=../src/main/resources -Dc71.violations=violations.tsv Lab ${*:-lint ratchet deprecation catalog}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
