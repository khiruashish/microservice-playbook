#!/usr/bin/env bash
# Chapter 61's experiment: 20 realistic bugs, each compiled into order-service in turn, against five test suites
# (unit, h2, postgres, contract, component). About 10 minutes. Needs a JDK 25 (JAVA_HOME), and PostgreSQL: set
# FF_PG_URL (e.g. jdbc:postgresql://127.0.0.1:5461/, user postgres, trust auth), or have docker, and a postgres:16
# container is started on port 5461 for the run.
#   ./run.sh             the mutation matrix, then the API checks
#   ./run.sh suites      only the five suites on the correct code
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C61_SOURCES:-build/src}
./build-libs.sh > /dev/null
L=build/lib
rm -rf build/main build/test build/tools build/work && mkdir -p build/main build/test build/tools build/work
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
cp -r ../src/test/resources/* build/test/
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "$L/*" tools/*.java

PG=${FF_PG_URL:-}
if [ -z "$PG" ]; then
  CID=$(docker run -d --rm -p 127.0.0.1:5461:5432 -e POSTGRES_HOST_AUTH_METHOD=trust postgres:16 -c fsync=off)
  trap 'docker rm -f "$CID" > /dev/null' EXIT
  until docker exec "$CID" pg_isready -q -h 127.0.0.1 2>/dev/null; do sleep 0.5; done
  PG=jdbc:postgresql://127.0.0.1:5461/
fi
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
if [ "${1:-}" = suites ]; then
  "$JAVA_HOME/bin/java" -Duser.timezone=UTC -Dff.pg.url="$PG" -cp "build/main:../src/main/resources:build/test:build/tools:$L/*" RunSuites unit h2 postgres contract component
  exit 0
fi
"$JAVA_HOME/bin/java" -Duser.timezone=UTC -Dff.pg.url="$PG" -cp "build/tools:$L/*" Mutate \
  ../src/main/java mutants.tsv build/work "build/test:build/tools:$L/*" unit h2 postgres contract component
echo "== apis"
./check_apis.sh "$SRC"
