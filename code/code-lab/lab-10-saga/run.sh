#!/usr/bin/env bash
# Lab 10: compiles chapter 14's saga engine, place-order saga and choreography with a bare JDK 25, then
#   1. SagaTrace: five scripted scenarios, orchestrated and choreographed, printing the saga log and the events;
#   2. SagaSimulation: 10,000 sagas with random faults and orchestrator crashes, with and without
#      an idempotent payment-service.
# No Maven, no Docker; about a second. Run it again after each exercise.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
CH14=../../chapters/ch14-saga/src/main/java
rm -rf build && mkdir -p build
${J}javac -d build $(find $CH14 src -name '*.java')
${J}java -Dstdout.encoding=UTF-8 -cp build dev.foodfleet.lab10.SagaTrace
echo
${J}java -Dstdout.encoding=UTF-8 -cp build dev.foodfleet.ch14.simulation.SagaSimulation
