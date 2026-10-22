#!/usr/bin/env bash
# Lab 11: compiles chapter 13's event-sourced rider wallet, event store and projections with a bare JDK 25
# (leaving out JdbcEventStore, the one class that needs Spring) and runs a rider's evening through them.
# Then, if PostgreSQL 16 is available, verify/event-store.sh checks concurrency and the position gap on it.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
CH13=../../chapters/ch13-cqrs-event-sourcing/src/main/java
rm -rf build && mkdir -p build
${J}javac -d build $(find $CH13 -name '*.java' ! -name JdbcEventStore.java)
${J}java -Dstdout.encoding=UTF-8 -cp build dev.foodfleet.ch13.WalletDemo
if [ -n "${PG_BIN:-}" ] || command -v pg_config >/dev/null; then echo; verify/event-store.sh; fi
