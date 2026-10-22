#!/usr/bin/env bash
# Compiles and tests the coding-round answers with a bare JDK 25.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
rm -rf build && mkdir -p build
${J}javac -d build $(find src -name '*.java')
${J}java -cp build dev.foodfleet.interview.CodingRoundsTest
