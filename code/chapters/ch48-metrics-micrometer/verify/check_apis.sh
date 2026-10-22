#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 48 relies on against the sources of Micrometer 1.17.1, Spring
# Framework 7.0.9 and Spring Boot 4.1.1 (cloned by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
M=$SRC/mm/micrometer-core/src/main/java/io/micrometer/core/instrument
P=$SRC/mm/implementations/micrometer-registry-prometheus/src/main/java/io/micrometer/prometheusmetrics
W=$SRC/sf/spring-web/src/main/java/org/springframework
B=$SRC/boot/module/spring-boot-micrometer-metrics/src/main/java/org/springframework/boot/micrometer/metrics

echo "-- Micrometer 1.17.1"
check "percentile histograms are off unless asked for"                  $M/distribution/DistributionStatisticConfig.java 'DEFAULT = builder\(\)\.percentilesHistogram\(false\)'
check "distribution statistics: expiry 2 min, 3 buffers by default"     $M/distribution/DistributionStatisticConfig.java '\.expiry\(Duration\.ofMinutes\(2\)\)'
check "...the Prometheus registry uses its step (1 min) as the expiry"  $P/PrometheusMeterRegistry.java '\.expiry\(prometheusConfig\.step\(\)\)'
check "...PrometheusConfig.step defaults to 1 minute"                   $P/PrometheusConfig.java 'orElse\(Duration\.ofMinutes\(1\)\)'
check "percentile windows rotate every expiry / buffers"                $M/distribution/AbstractTimeWindowHistogram.java 'getExpiry\(\)\)\.toMillis\(\)'
check "...max rotates every expiry, through every buffer"               $M/distribution/TimeWindowMax.java 'this\(clock, Objects\.requireNonNull\(config\.getExpiry\(\)\)\.toMillis\(\),'
check "timer histograms span 1 ms to 30 s by default"                   $M/AbstractTimerBuilder.java 'DEFAULT_MAXIMUM_EXPECTED_DURATION = Duration\.ofSeconds\(30\)'
check "Prometheus: quantiles only when there are no histogram buckets"  $P/PrometheusMeterRegistry.java 'if \(histogramCounts\.length == 0\) \{'
check "maximumAllowableTags: values beyond the limit get onMaxReached"  $M/config/MeterFilter.java 'if \(observedTagValues\.size\(\) >= maximumTagValues\) \{'
check "gauges hold their object weakly unless strongReference(true)"   $M/Gauge.java 'private boolean strongReference = false;'
check "the meter handler adds a <name>.active long-task timer"         $M/observation/DefaultMeterObservationHandler.java '\.longTaskTimer\(context\.getName\(\) \+ "\.active"'
check "...unless IgnoredMeters.LONG_TASK_TIMER"                         $M/observation/DefaultMeterObservationHandler.java 'LONG_TASK_TIMER'
check "HighCardinalityTagsDetector checks every 5 minutes by default"  $M/HighCardinalityTagsDetector.java 'DEFAULT_DELAY = Duration\.ofMinutes\(5\)'

echo "-- Spring Framework 7.0.9"
check "server uri tag: the matched path pattern"                        $W/http/server/observation/DefaultServerRequestObservationConvention.java 'return KeyValue\.of\(LowCardinalityKeyNames\.URI, pattern\);'
check "...else NOT_FOUND, REDIRECTION or UNKNOWN, never the raw path"   $W/http/server/observation/DefaultServerRequestObservationConvention.java 'URI_UNKNOWN = KeyValue\.of\(LowCardinalityKeyNames\.URI, "UNKNOWN"\)'
check "server http.url (high cardinality): the request URI"             $W/http/server/observation/DefaultServerRequestObservationConvention.java 'KeyValue\.of\(HighCardinalityKeyNames\.HTTP_URL, context\.getCarrier\(\)\.getRequestURI\(\)\)'
check "client uri tag: the path of the URI template, or none"           $W/http/client/observation/DefaultClientRequestObservationConvention.java 'KeyValue\.of\(LowCardinalityKeyNames\.URI, extractPath\(context\.getUriTemplate\(\)\)\)'
check "RestClient: the string given to uri(...) is the template"        $W/web/client/DefaultRestClient.java 'attribute\(URI_TEMPLATE_ATTRIBUTE, uriBuilder\.toUriString\(\)\);'

echo "-- Spring Boot 4.1.1"
check "manages Micrometer 1.17.1"                                       $SRC/boot/platform/spring-boot-dependencies/build.gradle 'library\("Micrometer", "1\.17\.1"\)'
check "manages the Prometheus client 1.7.0"                             $SRC/boot/platform/spring-boot-dependencies/build.gradle 'library\("Prometheus Client", "1\.7\.0"\)'
check "management.metrics.web.server.max-uri-tags defaults to 100"      $B/autoconfigure/MetricsProperties.java 'private int maxUriTags = 100;'
check "...Boot's cap denies new values once full, and warns once"       $B/MaximumAllowableTagsMeterFilter.java 'alreadyWarned\.compareAndSet\(false, true\)'
check "management.metrics.distribution.percentiles-histogram.* and .slo.*" $B/autoconfigure/MetricsProperties.java 'Map<String, ServiceLevelObjectiveBoundary\[\]> slo'
check "management.metrics.observations.ignored-meters"                  $B/autoconfigure/MetricsProperties.java 'private Set<IgnoredMeters> ignoredMeters'
check "management.observations.key-values"                              $SRC/boot/module/spring-boot-micrometer-observation/src/main/java/org/springframework/boot/micrometer/observation/autoconfigure/ObservationProperties.java 'private Map<String, String> keyValues'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
