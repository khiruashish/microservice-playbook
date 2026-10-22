#!/usr/bin/env bash
# Chapter 36 measurements. Needs only a JDK (21+): the protocol code (json/, jwt/, discovery/, pkce/, exchange/,
# authserver/) has no framework dependencies. The authorization server runs on a loopback port.
#   ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
SRC=../src/main/java
rm -rf build && mkdir -p build/main build/tools
javac -d build/main $(find $SRC -name '*.java' -not -path '*/web/*' -not -name 'OAuthApplication.java')
javac -d build/tools -cp build/main tools/Measure.java
for m in crypto validation revocation rotation exchange; do
  echo "== $m"
  java -cp build/main:build/tools Measure $m
  echo
done
