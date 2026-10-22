#!/usr/bin/env bash
# Lab 12, runnable part: three JDK services under the OpenTelemetry Java agent send traces and logs to a local
# Collector, which writes them to files and derives RED metrics; analyze.py then checks what arrived.
#   BIN=<dir with opentelemetry-javaagent.jar (2.32.0) and otelcol-contrib (0.162.0)>  verify/run.sh
# About a minute, most of it agent startup. Needs a JDK 21+, curl, python3.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
BIN=${BIN:?set BIN to the directory holding opentelemetry-javaagent.jar and otelcol-contrib}
J=${JAVA_HOME:+$JAVA_HOME/bin/}
export LAB12_OUT=${LAB12_OUT:-$(mktemp -d)}; mkdir -p "$LAB12_OUT"
rm -f "$LAB12_OUT"/*.json
${J}javac -d "$LAB12_OUT/classes" LabServices.java     # compiled: the agent doesn't attach to the source launcher
svc=()
pids=()
trap 'kill ${svc[@]} ${pids[@]} 2>/dev/null || true' EXIT

"$BIN/otelcol-contrib" --config collector-local.yaml > "$LAB12_OUT/collector.log" 2>&1 & pids+=($!)
start() {  # service port [downstream ports]
  ${J}java -javaagent:"$BIN/opentelemetry-javaagent.jar" \
    -Dotel.service.name=$1-service -Dotel.resource.attributes=deployment.environment.name=lab \
    -Dotel.exporter.otlp.protocol=http/protobuf -Dotel.exporter.otlp.endpoint=http://127.0.0.1:4318 \
    -Dotel.metrics.exporter=none -Dotel.bsp.schedule.delay=200 -Dotel.blrp.schedule.delay=200 \
    -Dotel.javaagent.logging=none \
    -cp "$LAB12_OUT/classes" LabServices "$@" > "$LAB12_OUT/$1.log" 2>&1 & svc+=($!)
}
start menu 18081
start kitchen 18082
start order 18080 18081 18082
for p in 18080 18081 18082; do
  for t in $(seq 120); do curl -s -m 2 -o /dev/null "http://127.0.0.1:$p/" && break; sleep 0.5; done
done

post() { curl -s -o /dev/null -w '%{http_code}\n' -X POST "http://127.0.0.1:18080/orders?$1"; }
for i in $(seq 20); do post "restaurant=r-$i&handoff=executor"; done | sort | uniq -c | sed 's/^/executor: /'
for i in $(seq 20); do post "restaurant=r-$i&handoff=thread"; done | sort | uniq -c | sed 's/^/thread:   /'
for i in $(seq 5); do post "restaurant=missing"; done | sort | uniq -c | sed 's/^/missing:  /'
sleep 2
kill ${svc[@]}; wait ${svc[@]} 2>/dev/null || true; svc=()   # the agents flush on shutdown
sleep 2                                                      # then the collector's batches
kill ${pids[@]}; wait 2>/dev/null || true; pids=()
python3 analyze.py "$LAB12_OUT"
