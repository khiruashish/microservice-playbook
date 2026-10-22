#!/usr/bin/env bash
# Chapter 60 measurements: menu-service started 10 times each as JVM, JVM warmed up, AppCDS, AOT cache and native
# image, at 1 CPU and at 0.25 CPU. About 25 minutes after build-libs.sh (itself about 6 minutes). Needs docker,
# a JDK 25 (JAVA_HOME) and GraalVM CE 25 (GRAALVM_HOME); port 18060 free.
#   ./run.sh            build, measure at 1 and 0.25 CPU, then the API checks
#   ./run.sh 1          only at 1 CPU (no rebuild if the images exist: SKIP_BUILD=1)
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C60_SOURCES:-build/src}
[ -n "${SKIP_BUILD:-}" ] || ./build-libs.sh
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$JAVA_HOME/bin/java" -cp build/tools Measure "$@"

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
