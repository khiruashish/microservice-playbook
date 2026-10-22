#!/usr/bin/env bash
# Chapter 43 measurements against Resilience4j 2.4.0 and Spring Framework 7.0.9, built from their release tags.
# Needs git and JDK 21+. About 8 minutes in all.
#   ./run.sh                                  every experiment, then the API checks
#   ./run.sh breaker slow timelimiter order ratelimiter schedules overload overhead
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
./build-libs.sh > /dev/null
LIBS=build/lib/resilience4j-2.4.0.jar:build/lib/spring-retry-7.0.9.jar
rm -rf build/main build/tools && mkdir -p build/main build/tools
javac -d build/main -cp $LIBS $(find ../src/main/java -name '*.java' -not -path '*/web/*')
javac -d build/tools -cp build/main:$LIBS tools/*.java
MODES=${*:-breaker slow timelimiter order ratelimiter schedules overload overhead}
for m in $MODES; do
  echo "== $m"
  java -Xmx1g -Dslf4j.internal.verbosity=ERROR -cp build/main:build/tools:$LIBS Measure "$m"
  echo
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "${C43_SOURCES:-build/src}"
