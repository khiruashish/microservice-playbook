#!/usr/bin/env bash
# Checks the test-tooling behaviours chapter 61 relies on against their sources (sparse clones made by build-libs.sh):
# JUnit 6.1.3, Spring Boot 4.1.1's spring-boot-starter-test, H2 2.5.252.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
J=$SRC/junit; T=$J/junit-platform-launcher/src/main/java/org/junit/platform/launcher
ST=$SRC/boot/starter/spring-boot-starter-test/build.gradle

echo "-- JUnit 6.1.3"
check "JUnit 6 runs on Java 17 and later"                              $J/gradle/plugins/common/src/main/kotlin/JavaLibraryExtension.kt 'mainJavaVersion\.convention\(JavaVersion\.VERSION_17\)'
check "suites selected by tag: TagFilter.includeTags"                   $T/TagFilter.java 'public static PostDiscoveryFilter includeTags\(String\.\.\. tagExpressions\)'
check "tag expressions: ! & | (e.g. 'unit | component')"               $T/tagexpression/Operators.java 'binaryOperator\("\|", 1'
check "...and any() / none()"                                           $T/tagexpression/ShuntingYard.java '"none\(\)"\.equalsIgnoreCase'
check "parallel execution: junit.jupiter.execution.parallel.enabled" $J/junit-jupiter-api/src/main/java/org/junit/jupiter/api/Constants.java 'PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME = "junit\.jupiter\.execution\.parallel\.enabled"'

echo "-- Spring Boot 4.1.1: spring-boot-starter-test"
check "JUnit Jupiter"                                                   $ST 'api\("org\.junit\.jupiter:junit-jupiter"\)'
check "AssertJ"                                                         $ST 'api\("org\.assertj:assertj-core"\)'
check "Mockito"                                                         $ST 'api\("org\.mockito:mockito-core"\)'
check "Awaitility (for asynchronous assertions)"                        $ST 'api\("org\.awaitility:awaitility"\)'
check "spring-test (MockMvc, the test context)"                         $ST 'api\("org\.springframework:spring-test"\)'

echo "-- H2 2.5.252, PostgreSQL mode"
check "ON CONFLICT DO NOTHING is parsed only without a conflict target" $SRC/h2/h2/src/main/org/h2/command/Parser.java 'readIfCompat\(ON, "CONFLICT", "DO", "NOTHING"\)'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
