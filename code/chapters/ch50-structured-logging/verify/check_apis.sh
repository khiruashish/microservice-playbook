#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 50 relies on against the sources of Logback 1.5.38, the OpenTelemetry
# Java instrumentation 2.32.0 and Spring Boot 4.1.1 (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
AB=$SRC/logback/logback-core/src/main/java/ch/qos/logback/core/AsyncAppenderBase.java
AC=$SRC/logback/logback-classic/src/main/java/ch/qos/logback/classic/AsyncAppender.java
JE=$SRC/logback/logback-classic/src/main/java/ch/qos/logback/classic/encoder/JsonEncoder.java
MDC=$SRC/otelji/instrumentation/logback/logback-mdc-1.0
MDCJ=$MDC/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/logback/mdc/v1_0
INC=$SRC/otelji/instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator
BL=$SRC/boot/core/spring-boot/src/main/java/org/springframework/boot/logging
META=$SRC/boot/core/spring-boot/src/main/resources/META-INF/additional-spring-configuration-metadata.json

echo "-- Logback 1.5.38"
check "AsyncAppender: queue of 256 by default"                           $AB 'DEFAULT_QUEUE_SIZE = 256;'
check "...discardingThreshold defaults to a fifth of the queue"         $AB 'discardingThreshold = queueSize / 5;'
check "...below the threshold, discardable events are dropped silently" $AB 'if \(isQueueBelowDiscardingThreshold\(\) && isDiscardable\(eventObject\)\) \{'
check "...discardable means TRACE, DEBUG and INFO"                       $AC 'return level\.toInt\(\) <= Level\.INFO_INT;'
check "...neverBlock false by default (callers block on a full queue)"  $AB 'boolean neverBlock = false;'
check "...neverBlock: offer(), which drops any level when full"         $AB 'blockingQueue\.offer\(eventObject\);'
check "...maxFlushTime 1000 ms on stop"                                  $AB 'DEFAULT_MAX_FLUSH_TIME = 1000;'
check "JsonEncoder: sequence number on by default"                      $JE 'private boolean withSequenceNumber = true;'
check "...nanoseconds on by default"                                     $JE 'private boolean withNanoseconds = true;'
check "...context (name, birthdate, properties) on by default"          $JE 'private boolean withContext = true;'
check "...raw arguments on by default"                                   $JE 'private boolean withArguments = true;'
check "...formatted message off by default (the template is written)"   $JE 'private boolean withFormattedMessage = false;'

echo "-- OpenTelemetry Java instrumentation 2.32.0"
check "logback-mdc: context captured when the logger calls appenders"    $MDCJ/LoggerInstrumentation.java 'CONTEXT\.set\(event, Java8BytecodeBridge\.currentContext\(\)\);'
check "...and added when the MDC map is read (async appenders included)" $MDCJ/LoggingEventInstrumentation.java 'namedOneOf\("getMDCPropertyMap", "getMdc"\)'
check "...keys trace_id, span_id, trace_flags"                           $INC/log/LoggingContextConstants.java 'TRACE_ID = "trace_id";'
check "...renamed with otel.instrumentation.common.logging.trace-id-key" $INC/config/internal/CommonConfig.java '"otel\.instrumentation\.common\.logging\.trace-id-key"'
check "...baggage into the MDC off by default"                           $MDC/metadata.yaml 'name: otel.instrumentation.logback-mdc.add-baggage'

echo "-- Spring Boot 4.1.1"
check "structured formats: ecs, gelf, logstash"                          $BL/structured/CommonStructuredLogFormat.java 'LOGSTASH\("logstash"\)'
check "logging.structured.format.console / .file"                        $META '"name": "logging.structured.format.file"'
check "ECS: @timestamp, log.level, log.logger, process.thread.name"      $BL/logback/ElasticCommonSchemaStructuredLogFormatter.java 'thread\.add\("name", ILoggingEvent::getThreadName\)'
check "...the formatted message (not the template)"                      $BL/logback/ElasticCommonSchemaStructuredLogFormatter.java 'members\.add\("message", ILoggingEvent::getFormattedMessage\);'
check "...every MDC entry and key-value pair as a field"                 $BL/logback/ElasticCommonSchemaStructuredLogFormatter.java 'pairs\.addMapEntries\(ILoggingEvent::getMDCPropertyMap\);'
check "...ecs.version 8.11"                                              $BL/logback/ElasticCommonSchemaStructuredLogFormatter.java 'ecs\.add\("version", "8\.11"\)'
check "logging.structured.json: include, exclude, rename, add, stack trace" $BL/structured/StructuredLoggingJsonProperties.java 'record StructuredLoggingJsonProperties\(Set<String> include, Set<String> exclude, Map<String, String> rename,'
check "...stack trace max-length and max-throwable-depth"                $META '"name": "logging.structured.json.stacktrace.max-throwable-depth"'
check "StructuredLogEncoder with a format, for logback-spring.xml"       $BL/logback/StructuredLogEncoder.java 'public void setFormat\(String format\)'
check "correlation pattern defaults to traceId(32),spanId(16) (Micrometer's MDC keys)" $BL/CorrelationIdFormatter.java 'DEFAULT = CorrelationIdFormatter\.of\("traceId\(32\),spanId\(16\)"\);'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
