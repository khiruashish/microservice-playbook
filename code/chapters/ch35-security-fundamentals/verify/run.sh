#!/usr/bin/env bash
# Chapter 35 measurements. Needs only a JDK (21+): the security code in identity/, orders/, auth/, outbound/ and
# threatmodel/ has no framework dependencies, so it compiles and runs without Maven.
#   ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
SRC=../src/main/java
rm -rf build && mkdir -p build/main build/tools
javac -d build/main $(find $SRC -name '*.java' -not -path '*/web/*' -not -name 'SecurityFundamentalsApplication.java')
javac -d build/tools -cp build/main tools/Measure.java
for m in authz throttle hashrate threatmodel; do
  echo "== $m"
  java -cp build/main:build/tools Measure $m
  echo
done
