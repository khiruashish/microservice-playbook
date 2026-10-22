#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 49 relies on against the sources of OpenTelemetry Java 1.66.0, the
# OpenTelemetry Java instrumentation 2.32.0 and Spring Boot 4.1.1 (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
API=$SRC/otel/api/all/src/main/java/io/opentelemetry/api
SDK=$SRC/otel/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/samplers
I=$SRC/otelji/instrumentation
K=$I/kafka/kafka-clients
TR=$SRC/boot/module/spring-boot-micrometer-tracing/src/main/java/org/springframework/boot/micrometer/tracing/autoconfigure/TracingProperties.java
OT=$SRC/boot/module/spring-boot-micrometer-tracing-opentelemetry/src/main/java/org/springframework/boot/micrometer/tracing/opentelemetry/autoconfigure
KP=$SRC/boot/module/spring-boot-kafka/src/main/java/org/springframework/boot/kafka/autoconfigure/KafkaProperties.java

echo "-- OpenTelemetry Java 1.66.0"
check "W3C trace context: traceparent and tracestate headers"           $API/trace/propagation/W3CTraceContextPropagator.java 'TRACE_PARENT = "traceparent"'
check "W3C baggage: one baggage header with every entry"                $API/baggage/propagation/W3CBaggagePropagator.java 'FIELD = "baggage"'
check "default propagators: tracecontext, baggage"                      $SRC/otel/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/PropagatorConfiguration.java 'DEFAULT_PROPAGATORS = Arrays\.asList\("tracecontext", "baggage"\)'
check "parent-based: a sampled remote parent is always followed"        $SDK/ParentBasedSampler.java 'remoteParentSampled == null \? Sampler\.alwaysOn\(\)'
check "Context.taskWrapping(ExecutorService)"                           $SRC/otel/context/src/main/java/io/opentelemetry/context/Context.java 'static ExecutorService taskWrapping\(ExecutorService executorService\)'

echo "-- OpenTelemetry Java instrumentation 2.32.0"
check "Kafka: producer propagation on by default"                       $K/kafka-clients-0.11/metadata.yaml 'name: otel.instrumentation.kafka.producer-propagation.enabled'
check "Kafka: receive telemetry off by default (consumers continue the producer's trace)" $K/kafka-clients-0.11/metadata.yaml 'only a span link connecting it to the producer trace'
check "Kafka: context written into the record's headers"                $K/kafka-clients-common-0.11/library/src/main/java/io/opentelemetry/instrumentation/kafkaclients/common/v0_11/internal/KafkaHeadersSetter.java 'headers\.remove\(key\)\.add\(key, value\.getBytes\(UTF_8\)\)'
check "Kafka: ConsumerRecords iterator(), records(), listIterator() wrapped" $K/kafka-clients-0.11/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/kafkaclients/v0_11/ConsumerRecordsInstrumentation.java 'named\("listIterator"\)'
check "Kafka: a record's process span ends at the next hasNext()"       $K/kafka-clients-common-0.11/library/src/main/java/io/opentelemetry/instrumentation/kafkaclients/common/v0_11/internal/TracingIterator.java 'closeScopeAndEndSpan\(\);'
check "NATS: publish and request are instrumented"                      $I/nats/nats-2.17/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/nats/v2_17/NatsInstrumentationModule.java 'ConnectionPublishInstrumentation'
check "NATS: consumers only through MessageHandler.onMessage"           $I/nats/nats-2.17/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/nats/v2_17/MessageHandlerInstrumentation.java 'implementsInterface\(named\("io\.nats\.client\.MessageHandler"\)\)'
check "executors: JDK executors and fork/join tasks carry context"      $I/executors/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/executors/ExecutorsInstrumentationModule.java 'JavaExecutorInstrumentation'
check "...virtual threads: VirtualThread carrier switching is instrumented" $I/executors/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/executors/VirtualThreadInstrumentation.java 'named\("switchToCarrierThread"\)'

echo "-- Spring Boot 4.1.1"
check "management.tracing.sampling.probability defaults to 0.1"         $TR 'private float probability = 0\.10f;'
check "propagation: produce W3C by default"                             $TR 'produce = List\.of\(PropagationType\.W3C\)'
check "...consume W3C, B3 and B3 multi by default"                      $TR 'consume = List\.of\(PropagationType\.values\(\)\)'
check "W3C with baggage: the W3C baggage propagator (every entry)"     $OT/CompositeTextMapPropagator.java 'composite\(W3CTraceContextPropagator\.getInstance\(\), W3CBaggagePropagator\.getInstance\(\)\)'
check "...remote-fields: additionally sent as their own headers"        $OT/OpenTelemetryPropagationConfigurations.java 'new BaggageTextMapPropagator\(remoteFields'
check "management.tracing.baggage.tag-fields"                           $TR 'private List<String> tagFields'
check "spring.kafka.template.observation-enabled (default false)"       $KP 'private boolean observationEnabled;'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
