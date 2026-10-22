#!/usr/bin/env bash
# Chapter 62's experiments against PostgreSQL 16 in containers and H2: test lifecycle (container per class, per run,
# reused, H2), isolation between tests, and what H2 can stand in for; then the JUnit suites and the API checks.
# About 5 minutes. Needs docker, a JDK 25 (JAVA_HOME). C62_IMAGE picks the PostgreSQL image (default c62/postgres:16
# when build-libs.sh could build it, else postgres:16).
#   ./run.sh                       everything
#   ./run.sh lifecycle isolation   some experiments
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C62_SOURCES:-build/src}
./build-libs.sh
IMAGE=${C62_IMAGE:-$(docker image inspect c62/postgres:16 > /dev/null 2>&1 && echo c62/postgres:16 || echo postgres:16)}
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
CP="build/main:build/test:build/tools:$L/*"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
echo "PostgreSQL image: $IMAGE; docker $(docker version --format '{{.Server.Version}}')"
"$JAVA_HOME/bin/java" -Duser.timezone=UTC -cp "$CP" -Dc62.image="$IMAGE" -Dc62.schema=../src/main/resources/db/schema.sql Lab "$@"

[ $# -gt 0 ] && exit 0
echo "== suites"
"$JAVA_HOME/bin/java" -Duser.timezone=UTC -cp "$CP" -Dff.pg.image="$IMAGE" -Dff.schema=../src/main/resources/db/schema.sql RunSuites postgres h2
echo "== apis"
./check_apis.sh "$SRC"
