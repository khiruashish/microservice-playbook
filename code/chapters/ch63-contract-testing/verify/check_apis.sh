#!/usr/bin/env bash
# Checks the contract-testing behaviours chapter 63 relies on against their sources (sparse clones made by
# build-libs.sh): Pact JVM 4.7.5, Spring Cloud Contract 5.0.3, oasdiff 1.34.0 and the Pact Broker 2.122.0.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
P=$SRC/pact/core/matchers/src/main/kotlin/au/com/dius/pact/core/matchers
O=$SRC/oasdiff/checker/rules.go
C=$SRC/scc

echo "-- Pact JVM 4.7.5"
check "responses: unexpected keys allowed (bodyContext allowUnexpectedKeys = true)" $P/ResponseMatching.kt 'val bodyContext = MatchingContext\(expected\.matchingRules\.rulesForCategory\("body"\),'
check "...the line after: true"                                          $P/ResponseMatching.kt '^      true, pluginConfiguration\)$'
check "regex matcher: the value's string form; null never matches"       $P/MatcherExecutor.kt 'val matches = if \(actual == null \|\| actual is JsonValue\.Null\) false else safeToString\(actual\)\.matches'
check "min-type matcher (eachLike) exists"                               $SRC/pact/core/model/src/main/kotlin/au/com/dius/pact/core/model/matchingrules/MatchingRules.kt 'data class MinTypeMatcher\(val min: Int\)'

echo "-- Pact Broker 2.122.0: can-i-deploy"
check "deployable only if every considered verification succeeded"       $SRC/broker/lib/pact_broker/matrix/deployment_status_summary.rb 'considered_rows\.all\?\(&:success\)'
check "...and unknown (nil) if a required integration has no verification" $SRC/broker/lib/pact_broker/matrix/deployment_status_summary.rb 'return nil if required_integrations_without_a_row\.any\?'

echo "-- Spring Cloud Contract 5.0.3"
check "contracts in Java: Contract.make"                                 $C/specs/spring-cloud-contract-spec-java/src/main/java/org/springframework/cloud/contract/spec/Contract.java 'public static Contract make\(Consumer<Contract> consumer\)'
check "stub runner modes: CLASSPATH, LOCAL, REMOTE"                      $C/spring-cloud-contract-stub-runner/src/main/java/org/springframework/cloud/contract/stubrunner/spring/StubRunnerProperties.java '^		REMOTE,'

echo "-- oasdiff 1.34.0: rule levels"
check "api-path-removed-without-deprecation: ERR"                         $O 'newBackwardCompatibilityRule\(APIPathRemovedWithoutDeprecationId, ERR,'
check "new-required-request-parameter: ERR"                               $O 'newBackwardCompatibilityRule\(NewRequiredRequestParameterId, ERR,'
check "new-optional-request-parameter: INFO"                              $O 'newBackwardCompatibilityRule\(NewOptionalRequestParameterId, INFO,'
check "response-required-property-removed: ERR"                           $O 'newBackwardCompatibilityRule\(ResponseRequiredPropertyRemovedId, ERR,'
check "response-optional-property-removed: INFO"                          $O 'newBackwardCompatibilityRule\(ResponseOptionalPropertyRemovedId, INFO,'
check "response-optional-property-added: INFO"                            $O 'newBackwardCompatibilityRule\(ResponseOptionalPropertyAddedId, INFO,'
check "response-property-type-changed: ERR"                               $O 'newBackwardCompatibilityRule\(ResponsePropertyTypeChangedId, ERR,'
check "response-property-became-nullable: ERR"                            $O 'newBackwardCompatibilityRule\(ResponsePropertyBecameNullableId, ERR,'
check "response-property-enum-value-added: ERR"                           $O 'newBackwardCompatibilityRule\(ResponsePropertyEnumValueAddedId, ERR,'
check "response-non-success-status-removed: INFO"                         $O 'newBackwardCompatibilityRule\(ResponseNonSuccessStatusRemovedId, INFO,'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
