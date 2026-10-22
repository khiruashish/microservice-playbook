#!/usr/bin/env bash
# Checks the tool behaviours chapter 65 relies on against their sources (sparse clones made by build-libs.sh):
# k6 2.3.0, Gatling 3.16.0, HdrHistogram 2.2.2, Toxiproxy 2.13.0 and ArchUnit 1.5.1.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
K=$SRC/k6/lib/executor
G=$SRC/gatling/gatling-core/src/main/scala/io/gatling/core/controller/inject
A=$SRC/archunit/archunit/src/main/java/com/tngtech/archunit

echo "-- k6 2.3.0: closed and open executors"
check "constant-vus: a fixed number of looping VUs (closed)"             $K/constant_vus.go '^type ConstantVUs struct'
check "constant-arrival-rate: iterations at a rate, with pre-allocated VUs (open)" $K/constant_arrival_rate.go 'PreAllocatedVUs null\.Int `json:"preAllocatedVUs"`'
check "...iterations it can't start are counted as dropped_iterations"    $K/constant_arrival_rate.go 'BuiltinMetrics\.DroppedIterations'
check "...and it warns when it runs out of VUs"                           $K/constant_arrival_rate.go 'Insufficient VUs, reached %d active VUs'

echo "-- Gatling 3.16.0"
check "open model: constantUsersPerSec"                                   $G/open/OpenInjectionSupport.scala 'def constantUsersPerSec\(rate: Double\)'
check "closed model: constantConcurrentUsers"                             $G/closed/ClosedInjectionSupport.scala 'def constantConcurrentUsers\(number: Int\)'

echo "-- HdrHistogram 2.2.2"
check "correcting a closed model's samples: recordValueWithExpectedInterval" $SRC/hdr/src/main/java/org/HdrHistogram/AbstractHistogram.java 'public void recordValueWithExpectedInterval\(final long value, final long expectedIntervalBetweenValueSamples\)'

echo "-- Toxiproxy 2.13.0"
check "latency toxic: latency +/- jitter on the data passing through"     $SRC/toxiproxy/toxics/latency.go 'passes data through with the a delay of latency \+/- jitter added'

echo "-- ArchUnit 1.5.1"
check "layered architecture rules (consideringAllDependencies)"           $A/library/Architectures.java 'public LayeredArchitecture consideringAllDependencies\(\)'
check "cycle detection stops at 100 cycles by default"                    $A/library/cycle_detection/CycleConfiguration.java 'MAX_NUMBER_OF_CYCLES_TO_DETECT_DEFAULT_VALUE = "100"'
check "freezing: violations stored in archunit_store by default"          $A/library/freeze/TextFileBasedViolationStore.java 'STORE_PATH_DEFAULT = "archunit_store"'
check "...and a missing store is an error unless creation is allowed"     $A/library/freeze/TextFileBasedViolationStore.java 'ALLOW_STORE_CREATION_DEFAULT = "false"'
check "general coding rule: no standard streams"                          $A/library/GeneralCodingRules.java 'NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS ='
check "importer: dependencies from what an ASM visitor sees (e.g. class literals in ldc)" $A/core/importer/JavaClassProcessor.java 'public void visitLdcInsn\(Object value\)'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
