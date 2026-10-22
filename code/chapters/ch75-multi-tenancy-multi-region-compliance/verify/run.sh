#!/usr/bin/env bash
# Chapter 75's experiments on PostgreSQL 16: three tenancy models at 10, 100 and 1,000 tenants; what a query that
# forgets its tenant returns; one big tenant against everyone's p99; and where an erased customer's data survives.
# About 5 minutes and 2 GB of disk. Needs a JDK 25 (JAVA_HOME), git and PostgreSQL 16 server binaries (PG_BIN, or
# pg_config on the PATH). Run as an ordinary user, or as root (PostgreSQL commands then run as the postgres user).
#   ./run.sh                              everything, then the tests and the API checks
#   ./run.sh models leaks noisy erasure
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
export PG_BIN=${PG_BIN:-$(pg_config --bindir)}
SRC=${C75_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main -cp "$L/*" $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$PG_BIN/postgres" --version
"$JAVA_HOME/bin/java" -Duser.timezone=UTC -Dc75.dir=${C75_DIR:-/tmp/c75pg} -cp "build/main:build/tools:$L/*" Lab ${*:-models leaks noisy erasure}
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
