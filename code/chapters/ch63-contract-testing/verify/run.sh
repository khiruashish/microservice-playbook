#!/usr/bin/env bash
# Chapter 63's experiment: 12 provider changes against an OpenAPI diff, two consumers' contracts and an E2E suite,
# compared with what actually breaks. Under a minute. Needs a JDK 25 (JAVA_HOME).
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C63_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools build/work && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools Lab ../src/main/java ../src/main/resources/openapi/menu-service.json \
  ../src/test/resources/contracts changes.tsv build/work data-changes.tsv
echo "== tests"
(cd .. && "$JAVA_HOME/bin/java" -cp "verify/build/main:verify/build/test:verify/build/tools:verify/$L/*" RunSuites "none()")
echo "== apis"
./check_apis.sh "$SRC"
