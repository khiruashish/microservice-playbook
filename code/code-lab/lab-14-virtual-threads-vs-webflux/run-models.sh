#!/usr/bin/env bash
# Lab 14's measured part: chapter 66's quote endpoint in four concurrency models (platform threads, virtual threads,
# an async chain on two event-loop threads, virtual threads with a concurrency limit) under open load, on the JDK's
# HTTP server and client. No Maven. About 7 minutes; keep the machine otherwise idle.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
CH66=../../chapters/ch66-latency-virtual-threads-reactive
rm -rf build && mkdir -p build/main build/tools
${J}javac --release 21 -nowarn -d build/main $(find $CH66/src/main/java -name '*.java')
${J}javac -nowarn -d build/tools -cp build/main $CH66/verify/tools/Lab.java
${J}java -cp build/main:build/tools -Dc66.classes="$PWD/build/main" -Dc66.jdk21="$(command -v ${J}java)" Lab models
