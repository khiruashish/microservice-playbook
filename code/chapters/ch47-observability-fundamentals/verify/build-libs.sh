#!/usr/bin/env bash
# Downloads the binaries chapter 47's experiments run (OpenTelemetry Java agent 2.32.0, OpenTelemetry Collector
# Contrib 0.162.0, Prometheus 3.15.0; Linux x86-64) into build/bin, and the sources check_apis.sh reads into
# build/src (or $C47_SOURCES): OpenTelemetry Java 1.66.0 (the agent's SDK), the Java instrumentation 2.32.0,
# the Collector Contrib 0.162.0, Prometheus 3.15.0 and Spring Boot 4.1.1, sparse clones of their release tags.
set -euo pipefail
cd "$(dirname "$0")"
BIN=build/bin
SRC=${C47_SOURCES:-build/src}
mkdir -p "$BIN" "$SRC"

fetch() {   # url, file
  [ -s "$2" ] || curl -sSfL -o "$2" "$1"
}
fetch https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.32.0/opentelemetry-javaagent.jar \
  "$BIN/opentelemetry-javaagent.jar"
if [ ! -x "$BIN/otelcol-contrib" ]; then
  fetch https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.162.0/otelcol-contrib_0.162.0_linux_amd64.tar.gz \
    "$BIN/otelcol.tgz"
  tar xzf "$BIN/otelcol.tgz" -C "$BIN" otelcol-contrib && rm "$BIN/otelcol.tgz"
fi
if [ ! -x "$BIN/prometheus" ]; then
  fetch https://github.com/prometheus/prometheus/releases/download/v3.15.0/prometheus-3.15.0.linux-amd64.tar.gz "$BIN/prom.tgz"
  tar xzf "$BIN/prom.tgz" -C "$BIN" --strip-components=1 prometheus-3.15.0.linux-amd64/prometheus \
    prometheus-3.15.0.linux-amd64/promtool && rm "$BIN/prom.tgz"
fi

clone() {   # dir, repo, tag, sparse paths...
  local dir=$SRC/$1 repo=$2 tag=$3; shift 3
  [ -d "$dir" ] && return
  git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
  (cd "$dir" && git sparse-checkout set --no-cone "$@")
}
clone otel open-telemetry/opentelemetry-java v1.66.0 \
  '/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/samplers/' \
  '/sdk/metrics/src/main/java/io/opentelemetry/sdk/metrics/export/PeriodicMetricReaderBuilder.java' \
  '/sdk-extensions/autoconfigure/src/main/java/'
clone otelji open-telemetry/opentelemetry-java-instrumentation v2.32.0 \
  '/instrumentation/java-http-server/' '/instrumentation/java-http-client/metadata.yaml' \
  '/instrumentation/resources/library/src/main/java/'
clone colc open-telemetry/opentelemetry-collector-contrib v0.162.0 \
  '/processor/tailsamplingprocessor/*.go' '/processor/tailsamplingprocessor/internal/sampling/latency.go' \
  '/processor/probabilisticsamplerprocessor/*.go' \
  '/exporter/fileexporter/*.go' '/exporter/prometheusexporter/*.go' '/pkg/resourcetotelemetry/'
clone prom prometheus/prometheus v3.15.0 '/config/config.go'
clone boot spring-projects/spring-boot v4.1.1 \
  '/module/spring-boot-micrometer-tracing/src/main/' \
  '/module/spring-boot-micrometer-tracing-opentelemetry/src/main/' \
  '/module/spring-boot-opentelemetry/src/main/' \
  '/core/spring-boot/src/main/java/org/springframework/boot/logging/LoggingSystemProperty.java' \
  '/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/'
echo "binaries in $BIN, sources in $SRC"
