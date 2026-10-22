#!/usr/bin/env bash
# Chapter 77's experiments: an LLM gateway's deadlines and retries against a stub provider whose streams stall and
# whose limit answers 429; a chat feature and a batch job sharing one provider limit; exact, normalised and similarity
# caches over a day of support questions; and an MCP server (protocol 2026-07-28): its checks, latency, and a tool that
# trusted its arguments. About 13 minutes. Needs a JDK 25 (JAVA_HOME) and git.
#   ./run.sh                            everything, then the tests and the API checks
#   ./run.sh deadlines quota cache mcp
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C77_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/main:build/tools Lab ${*:-deadlines quota cache mcp}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
