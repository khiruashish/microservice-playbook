#!/usr/bin/env bash
# Chapter 53 measurements with Docker and a local registry: images built from scratch (no base image is pulled),
# runtimes from $JAVA_HOME (a JDK 25) via jlink. About 10 minutes. Needs docker, git, curl, unzip and Linux x86-64.
#   ./run.sh                                  everything, then the API checks
#   ./run.sh images jvm startup runtime
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
export JAVAC=$JAVA_HOME/bin/javac
./build-libs.sh > /dev/null
SRC=${C53_SOURCES:-build/src}
DEPS=$(find build/lib/deps -mindepth 1 -maxdepth 1 -type d | paste -sd:)
rm -rf build/app && mkdir -p build/app/main build/app/tools
$JAVAC --release 25 -nowarn -d build/app/main $(find ../src/main/java -name '*.java')
$JAVAC --release 25 -nowarn -XDsuppressNotes -d build/app/tools -cp "build/app/main:$DEPS" tools/*.java
$JAVA_HOME/bin/java -version 2>&1 | head -1
docker version --format 'Docker {{.Server.Version}}'
MODES=${*:-images jvm startup runtime}
for m in $MODES; do
  echo "== $m"
  $JAVA_HOME/bin/java -cp build/app/main:build/app/tools -Dc53.work=/tmp/c53 -Dc53.jdk="$JAVA_HOME" -Dc53.app="$PWD/build/app" \
    -Dc53.libs="$PWD/build/lib/deps" -Dc53.bin="$PWD/build/bin" Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
