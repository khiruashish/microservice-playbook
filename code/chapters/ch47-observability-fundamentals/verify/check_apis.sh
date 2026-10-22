#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 47 relies on against the sources of OpenTelemetry Java 1.66.0, the
# OpenTelemetry Java instrumentation 2.32.0, OpenTelemetry Collector Contrib 0.162.0, Prometheus 3.15.0 and
# Spring Boot 4.1.1 (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
O=$SRC/otel
A=$O/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure
R=$O/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/samplers/TraceIdRatioBasedSampler.java
I=$SRC/otelji/instrumentation
C=$SRC/colc
T=$C/processor/tailsamplingprocessor
B=$SRC/boot

echo "-- OpenTelemetry Java SDK 1.66.0 (inside the agent)"
check "otel.traces.sampler defaults to parentbased_always_on"      $A/TracerProviderConfiguration.java 'getString\("otel.traces.sampler", PARENTBASED_ALWAYS_ON\)'
check "trace-ID ratio: keep if |lower 64 bits| < ratio x Long.MAX_VALUE" $R 'Math.abs\(getTraceIdRandomPart\(traceId\)\) < idUpperBound'
check "...the lower 64 bits are hex digits 16..31 of the trace ID"     $R 'longFromBase16String\(traceId, 16\)'
check "...bounds: ratio x Long.MAX_VALUE"                             $R 'idUpperBound = \(long\) \(ratio \* Long.MAX_VALUE\);'
check "metrics are exported every minute unless configured"           $O/sdk/metrics/src/main/java/io/opentelemetry/sdk/metrics/export/PeriodicMetricReaderBuilder.java 'DEFAULT_SCHEDULE_DELAY_MINUTES = 1;'
check "otel.resource.disabled.keys removes resource attributes"       $A/ResourceConfiguration.java 'DISABLED_ATTRIBUTE_KEYS = "otel.resource.disabled.keys"'

echo "-- OpenTelemetry Java instrumentation 2.32.0"
check "the JDK's HTTP server is instrumented (javahttpserver)"        $I/java-http-server/metadata.yaml 'display_name: Java HTTP Server'
check "...with http.server.capture-request-headers"                   $I/java-http-server/metadata.yaml 'ref: http.server.capture-request-headers'
check "process resource: the full command line, as process.command_args" $I/resources/library/src/main/java/io/opentelemetry/instrumentation/resources/ProcessResource.java 'attributes.put\(PROCESS_COMMAND_ARGS, commandArgs\)'
check "...masking only -D properties named *password* or *secret*"   $I/resources/library/src/main/java/io/opentelemetry/instrumentation/resources/ProcessResource.java 'Pattern.compile\("\(-D\.\*\(password\|secret\)\.\*=\)\.\*"'

echo "-- OpenTelemetry Collector Contrib 0.162.0"
check "tail_sampling: decision_wait defaults to 30 s"                 $T/factory.go 'DecisionWait: +30 \* time.Second,'
check "tail_sampling: num_traces defaults to 50,000"                  $T/factory.go 'NumTraces: +50000,'
check "tail_sampling latency: first span start to last span end"      $T/internal/sampling/latency.go 'ms := int64\(maxTime-minTime\) / int64\(time.Millisecond\)'
check "probabilistic_sampler: hash_seed, equalizing, proportional modes" $C/processor/probabilisticsamplerprocessor/config.go '"proportional": Using an OTel-specified consistent sampling'
check "file exporter: format proto"                                   $C/exporter/fileexporter/config.go 'proto:  OTLP binary protobuf bytes'
check "prometheus exporter: resource_to_telemetry_conversion deprecated" $C/exporter/prometheusexporter/config.go 'Deprecated: Use ResourceConstantLabels instead.'
check "...resource_constant_labels with included / excluded"          $C/pkg/resourcetotelemetry/resource_to_telemetry.go 'Included \[\]string `mapstructure:"included"`'

echo "-- Prometheus 3.15.0"
check "scrape_interval defaults to 1 m"                               $SRC/prom/config/config.go 'ScrapeInterval: +model.Duration\(1 \* time.Minute\),'
check "evaluation_interval defaults to 1 m"                           $SRC/prom/config/config.go 'EvaluationInterval: +model.Duration\(1 \* time.Minute\),'

echo "-- Spring Boot 4.1.1"
check "management.tracing.sampling.probability defaults to 0.1"       $B/module/spring-boot-micrometer-tracing/src/main/java/org/springframework/boot/micrometer/tracing/autoconfigure/TracingProperties.java 'private float probability = 0.10f;'
check "management.opentelemetry.tracing.export.otlp.*"                $B/module/spring-boot-micrometer-tracing-opentelemetry/src/main/java/org/springframework/boot/micrometer/tracing/opentelemetry/autoconfigure/otlp/OtlpTracingProperties.java '@ConfigurationProperties\("management.opentelemetry.tracing.export.otlp"\)'
check "management.opentelemetry.logging.export.otlp.*"                $B/module/spring-boot-opentelemetry/src/main/java/org/springframework/boot/opentelemetry/autoconfigure/logging/otlp/OtlpLoggingProperties.java '@ConfigurationProperties\("management.opentelemetry.logging.export.otlp"\)'
check "management.opentelemetry.resource-attributes"                  $B/module/spring-boot-opentelemetry/src/main/java/org/springframework/boot/opentelemetry/autoconfigure/OpenTelemetryProperties.java 'private Map<String, String> resourceAttributes'
check "logging.structured.format.console (ecs, gelf, logstash)"       $B/core/spring-boot/src/main/java/org/springframework/boot/logging/LoggingSystemProperty.java '"logging.structured.format.console"'
check "logging.pattern.correlation"                                   $B/core/spring-boot/src/main/java/org/springframework/boot/logging/LoggingSystemProperty.java '"logging.pattern.correlation"'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
