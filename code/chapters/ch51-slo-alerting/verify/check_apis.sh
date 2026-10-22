#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 51 relies on against the sources of Prometheus 3.15.0, Alertmanager
# 0.34.1, Micrometer 1.17.1 and Spring Boot 4.1.1 (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
P=$SRC/prom
AM=$SRC/am
MM=$SRC/mm/micrometer-core/src/main/java/io/micrometer/core/instrument
BM=$SRC/boot/module/spring-boot-micrometer-metrics/src/main/java/org/springframework/boot/micrometer/metrics/autoconfigure

echo "-- Prometheus 3.15.0"
check "alerts: pending until the condition has held for 'for', then firing" $P/rules/alerting.go 'if a\.State == StatePending && ts\.Sub\(a\.ActiveAt\) >= r\.holdDuration \{'
check "...keep_firing_for exists to hold an alert after the condition clears" $P/rules/alerting.go 'keepFiringFor +time\.Duration'
check "histogram_fraction works on classic (le) histograms"       $P/promql/quantile.go '^// BucketFraction is a version of HistogramFraction for classic histograms\.'
check "...interpolating linearly inside a bucket"                  $P/promql/functions.go 'F: +BucketFraction\(lower, upper, mb\.buckets\),'
check "server: --query.max-samples defaults to 50,000,000"        $P/cmd/prometheus/main.go 'Default\("50000000"\)\.IntVar\(&cfg\.queryMaxSamples\)'
check "promtool rule tests: 10,000 samples per query"             $P/promql/promqltest/test.go 'MaxSamples: +10000,'

echo "-- Alertmanager 0.34.1"
check "root route defaults: group_wait 30s"                       $AM/dispatch/route.go 'GroupWait: +30 \* time\.Second,'
check "...group_interval 5m"                                      $AM/dispatch/route.go 'GroupInterval: +5 \* time\.Minute,'
check "...repeat_interval 4h"                                     $AM/dispatch/route.go 'RepeatInterval: +4 \* time\.Hour,'
check "inhibit rules: source and target must agree on 'equal' labels" $AM/inhibit/inhibit.go 'Equal map\[model\.LabelName\]struct\{\}'

echo "-- Micrometer 1.17.1"
check "timers' histogram range starts at 1 ms"                    $MM/AbstractTimerBuilder.java 'DEFAULT_MINIMUM_EXPECTED_DURATION = Duration\.ofMillis\(1\);'
check "...and ends at 30 s"                                       $MM/AbstractTimerBuilder.java 'DEFAULT_MAXIMUM_EXPECTED_DURATION = Duration\.ofSeconds\(30\);'
check "percentile-histogram buckets are a fixed pre-computed series" $MM/distribution/PercentileHistogramBuckets.java 'private static final NavigableSet<Double> PERCENTILE_BUCKETS;'

echo "-- Spring Boot 4.1.1"
check "management.metrics.distribution.percentiles-histogram.<meter>" $BM/MetricsProperties.java 'private final Map<String, Boolean> percentilesHistogram = new LinkedHashMap<>\(\);'
check "management.metrics.distribution.slo.<meter> (extra buckets)"    $BM/MetricsProperties.java 'private final Map<String, ServiceLevelObjectiveBoundary\[\]> slo = new LinkedHashMap<>\(\);'
check "...applied to matching meters by PropertiesMeterFilter"          $BM/PropertiesMeterFilter.java 'serviceLevelObjectives'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
