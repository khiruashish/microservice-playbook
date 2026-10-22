#!/usr/bin/env bash
# Chapter 69's experiments: producer batching, compression and in-flight requests, on loopback and with a 1 ms round
# trip; consumer parallelism by partitions, by key and by batch; and a consumer's writes to PostgreSQL one record or
# one poll at a time. About 10 minutes. Needs a JDK 25 (JAVA_HOME), Go 1.24+ and Docker.
#   ./run.sh                     everything, then the tests and the API checks
#   ./run.sh producer consumer sink
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C69_SOURCES:-build/src}
./build-libs.sh
L=build/lib
rm -rf build/main build/test build/tools && mkdir -p build/main build/test build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main -cp "$L/*" $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/test -cp "build/main:$L/*" $(find ../src/test/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp "build/main:$L/*" tools/*.java
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
go version

broker() {   # port, rtt: starts a broker in the background and prints its pid
  build/kafkalab broker -port "$1" -rtt "$2" > /dev/null &
  echo $!
}
for mode in ${*:-producer consumer sink}; do
  case $mode in
    producer)
      for rtt in 0 1ms; do
        echo "== producer (round trip: ${rtt/0/loopback})"
        pid=$(broker 19092 $rtt); sleep 1
        build/kafkalab producer -broker 127.0.0.1:19092 -broker-pid "$pid"
        kill "$pid"; wait "$pid" 2>/dev/null || true
        echo
      done ;;
    consumer)
      echo "== consumer"
      pid=$(broker 19092 0); sleep 1
      build/kafkalab consumer -broker 127.0.0.1:19092
      kill "$pid"; wait "$pid" 2>/dev/null || true
      echo ;;
    sink)
      "$JAVA_HOME/bin/java" -Duser.timezone=UTC -cp "build/main:build/tools:$L/*" Lab sink ;;
  esac
done
[ $# -gt 0 ] && exit 0
echo "== tests"
"$JAVA_HOME/bin/java" -cp "build/main:build/test:build/tools:$L/*" RunSuites unit
echo "== apis"
./check_apis.sh "$SRC"
