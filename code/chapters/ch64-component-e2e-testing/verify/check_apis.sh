#!/usr/bin/env bash
# Checks the behaviours chapter 64 relies on against their sources (sparse clones made by build-libs.sh): Awaitility
# 4.3.0, Argo CD 3.5.5's ApplicationSet pull-request generator, and Spring Boot 4.1.1's @SpringBootTest.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
A=$SRC/awaitility/awaitility/src/main/java/org/awaitility
G=$SRC/argocd/applicationset/generators/pull_request.go

echo "-- Awaitility 4.3.0: await().until(...) polls"
check "every 100 ms by default"                                           $A/Awaitility.java 'DEFAULT_POLL_INTERVAL = new FixedPollInterval\(ONE_HUNDRED_MILLISECONDS\)'
check "for at most 10 s by default"                                       $A/Awaitility.java 'defaultWaitConstraint = AtMostWaitConstraint\.TEN_SECONDS;'
check "...TEN_SECONDS is 10 s"                                             $A/constraint/AtMostWaitConstraint.java 'TEN_SECONDS = new AtMostWaitConstraint\(Duration\.ofSeconds\(10\)\)'

echo "-- Argo CD 3.5.5: an environment per pull request"
check "ApplicationSet pull-request generator: number and branch slug parameters" $G '"branch_slug": +slug\.Make\(pull\.Branch\)'
check "...the head commit, to deploy exactly what was pushed"             $G '"head_sha": +pull\.HeadSHA'
check "...polls the Git host every 30 minutes unless told (or a webhook)" $G 'DefaultPullRequestRequeueAfter = 30 \* time\.Minute'

echo "-- Spring Boot 4.1.1"
check "@SpringBootTest(webEnvironment = RANDOM_PORT): the real server"    $SRC/boot/core/spring-boot-test/src/main/java/org/springframework/boot/test/context/SpringBootTest.java 'RANDOM_PORT\(true\)'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
