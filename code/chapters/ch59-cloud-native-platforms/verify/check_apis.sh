#!/usr/bin/env bash
# Checks the AWS SDK for Java 2.56.0 and Kubernetes 1.37.1 behaviours chapter 59 relies on against their sources
# (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
R=$SRC/aws/core/retries/src/main/java/software/amazon/awssdk/retries
SPI=$SRC/aws/core/retries-spi/src/main/java/software/amazon/awssdk/retries/api/internal/backoff
K=$SRC/k8s

echo "-- AWS SDK for Java 2.56.0: retries"
check "standard: 3 attempts"                                           $R/DefaultRetryStrategy.java 'static final int MAX_ATTEMPTS = 3;'
check "standard: 100 ms base delay (v2.0 constants)"                    $R/DefaultRetryStrategy.java 'BASE_DELAY_V20 = Duration\.ofMillis\(100\)'
check "...1 s base delay after throttling"                              $R/DefaultRetryStrategy.java 'THROTTLED_BASE_DELAY = Duration\.ofSeconds\(1\)'
check "...a 500-token retry quota, 5 tokens per retry"                  $R/DefaultRetryStrategy.java 'TOKEN_BUCKET_SIZE = 500'
check "...DEFAULT_EXCEPTION_TOKEN_COST_V20 = 5"                         $R/DefaultRetryStrategy.java 'DEFAULT_EXCEPTION_TOKEN_COST_V20 = 5'
check "legacy: 4 attempts, throttling retries free"                     $R/DefaultRetryStrategy.java 'THROTTLE_EXCEPTION_TOKEN_COST = 0'
check "a success returns at least one token"                            $R/internal/BaseRetryStrategy.java 'int capacityReleased = Math\.max\(token\.capacityAcquired\(\), 1\);'
check "full jitter: random(0, min(max, base x 2^(attempt-2)))"          $SPI/ExponentialDelayWithJitter.java 'min\(maxDelay, baseDelay \* \(1 << \(attempt - 2\)\)\)'
check "adaptive: rate cut to 70% on throttling (BETA = 0.7)"            $R/internal/ratelimiter/RateLimiterTokenBucket.java 'private static final double BETA = 0\.7;'
check "...cubic recovery (SCALE_CONSTANT = 0.4)"                        $R/internal/ratelimiter/RateLimiterTokenBucket.java 'private static final double SCALE_CONSTANT = 0\.4;'
check "new defaults behind aws.newRetries2026"                          $SRC/aws/core/sdk-core/src/main/java/software/amazon/awssdk/core/SdkSystemSetting.java 'AWS_NEW_RETRIES_2026\("aws\.newRetries2026"'

echo "-- Kubernetes 1.37.1: service account tokens"
check "projected tokens: at least 10 minutes"                           $K/pkg/apis/core/validation/validation.go 'may not specify a duration less than 10 minutes'
check "kubelet refreshes at 80% of the lifetime, or after 24 h"         $K/pkg/kubelet/token/token_manager.go 'requiresRefresh returns true if the token is older than 80% of its total'
check "default tokens extended to a year (ExpirationExtensionSeconds)"  $K/pkg/serviceaccount/claims.go 'ExpirationExtensionSeconds = 24 \* 365 \* 60 \* 60'
check "bound tokens invalid once their pod is gone"                     $K/pkg/serviceaccount/claims.go 'Make sure token hasn.t been invalidated by deletion of the pod'
check "API server caches successful token checks for 10 s"              $K/pkg/kubeapiserver/options/authentication.go 'TokenSuccessCacheTTL: 10 \* time\.Second'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
