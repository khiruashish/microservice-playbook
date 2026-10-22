#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 43 relies on against the sources of Resilience4j 2.4.0,
# Spring Framework 7.0.9 and Spring Cloud CircuitBreaker 5.0.2 (Spring Cloud 2025.1.2).
# Usage: ./check_apis.sh <sources dir>   (the one build-libs.sh fills; Spring Cloud CircuitBreaker is cloned here)
set -uo pipefail
SRC=$1
R=$SRC/r4j SF=$SRC/sf SCCB=$SRC/sccb
if [ ! -d "$SCCB/.git" ]; then
  git -c advice.detachedHead=false clone -q --depth 1 --branch v5.0.2 --filter=blob:none --sparse \
    https://github.com/spring-cloud/spring-cloud-circuitbreaker.git "$SCCB"
  git -C "$SCCB" sparse-checkout set --no-cone /spring-cloud-circuitbreaker-dependencies/pom.xml
fi
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
CB=$R/resilience4j-circuitbreaker/src/main/java/io/github/resilience4j/circuitbreaker
RT=$R/resilience4j-retry/src/main/java/io/github/resilience4j/retry
CORE=$R/resilience4j-core/src/main/java/io/github/resilience4j/core
RL=$R/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter
BH=$R/resilience4j-bulkhead/src/main/java/io/github/resilience4j/bulkhead
TL=$R/resilience4j-timelimiter/src/main/java/io/github/resilience4j/timelimiter
S6=$R/resilience4j-spring6/src/main/java/io/github/resilience4j/spring6
SC=$SF/spring-core/src/main/java/org/springframework
RA=$SF/spring-context/src/main/java/org/springframework/resilience

echo "-- Resilience4j 2.4.0: circuit breaker defaults"
check "failureRateThreshold 50%"                          $CB/CircuitBreakerConfig.java 'DEFAULT_FAILURE_RATE_THRESHOLD = 50;'
check "slowCallRateThreshold 100%"                        $CB/CircuitBreakerConfig.java 'DEFAULT_SLOW_CALL_RATE_THRESHOLD = 100;'
check "slowCallDurationThreshold 60 s"                    $CB/CircuitBreakerConfig.java 'DEFAULT_SLOW_CALL_DURATION_THRESHOLD = 60;'
check "waitDurationInOpenState 60 s"                      $CB/CircuitBreakerConfig.java 'DEFAULT_WAIT_DURATION_IN_OPEN_STATE = 60;'
check "permittedNumberOfCallsInHalfOpenState 10"          $CB/CircuitBreakerConfig.java 'DEFAULT_PERMITTED_CALLS_IN_HALF_OPEN_STATE = 10;'
check "slidingWindowSize 100, minimumNumberOfCalls 100"   $CB/CircuitBreakerConfig.java 'DEFAULT_MINIMUM_NUMBER_OF_CALLS = 100;'
check "slidingWindowType COUNT_BASED"                     $CB/CircuitBreakerConfig.java 'DEFAULT_SLIDING_WINDOW_TYPE = SlidingWindowType.COUNT_BASED;'
check "every exception recorded as a failure"             $CB/CircuitBreakerConfig.java 'DEFAULT_RECORD_EXCEPTION_PREDICATE = throwable -> true;'
check "no automatic OPEN -> HALF_OPEN"                    $CB/CircuitBreakerConfig.java 'automaticTransitionFromOpenToHalfOpenEnabled = false;'
check "window synchronization SYNCHRONIZED or LOCK_FREE"  $CB/CircuitBreakerConfig.java 'LOCK_FREE,'
check "count window guarded by a ReentrantLock"           $CORE/metrics/FixedSizeSlidingWindowMetrics.java 'new ReentrantLock\(\)'

echo "-- Resilience4j 2.4.0: retry, rate limiter, bulkhead, time limiter"
check "Retry: 3 attempts, 500 ms apart"                   $RT/RetryConfig.java 'DEFAULT_WAIT_DURATION = 500;'
check "Retry: every exception retried by default"         $RT/RetryConfig.java 'DEFAULT_RECORD_FAILURE_PREDICATE = throwable -> true;'
check "Retry: ignoreExceptions(...) replaces the list"    $RT/RetryConfig.java 'this.ignoreExceptions = errorClasses != null \? errorClasses : new Class\[0\];'
check "exception lists match the exception's own class"   $CORE/predicate/PredicateCreator.java 'exClass.isAssignableFrom\(e.getClass\(\)\)'
check "random backoff: randomization factor 0.5"          $CORE/IntervalFunction.java 'DEFAULT_RANDOMIZATION_FACTOR = 0.5;'
check "RateLimiter: 50 permits per 500 ns"                $RL/RateLimiterConfig.java 'limitRefreshPeriod = Duration.ofNanos\(500\);'
check "RateLimiter: callers wait up to 5 s"               $RL/RateLimiterConfig.java 'timeoutDuration = Duration.ofSeconds\(5\);'
check "Bulkhead: 25 concurrent calls, no wait"            $BH/BulkheadConfig.java 'DEFAULT_MAX_WAIT_DURATION = Duration.ofSeconds\(0\);'
check "ThreadPoolBulkhead: queue of 100"                  $BH/ThreadPoolBulkheadConfig.java 'DEFAULT_QUEUE_CAPACITY = 100;'
check "TimeLimiter: 1 s, cancelRunningFuture true"        $TL/TimeLimiterConfig.java 'private boolean cancelRunningFuture = true;'
check "Hedge module"                                      $R/resilience4j-hedge/src/main/java/io/github/resilience4j/hedge/Hedge.java 'CompletableFuture<T> submit'

echo "-- Resilience4j 2.4.0: Spring integration"
check "aspect order: Retry outermost (LOWEST - 5)"        $S6/retry/configure/RetryConfigurationProperties.java 'retryAspectOrder = Ordered.LOWEST_PRECEDENCE - 5;'
check "aspect order: CircuitBreaker (LOWEST - 4)"         $S6/circuitbreaker/configure/CircuitBreakerConfigurationProperties.java 'circuitBreakerAspectOrder = Ordered.LOWEST_PRECEDENCE - 4;'
check "aspect order: RateLimiter (LOWEST - 3)"            $S6/ratelimiter/configure/RateLimiterConfigurationProperties.java 'rateLimiterAspectOrder = Ordered.LOWEST_PRECEDENCE - 3;'
check "aspect order: TimeLimiter (LOWEST - 2)"            $S6/timelimiter/configure/TimeLimiterConfigurationProperties.java 'timeLimiterAspectOrder = Ordered.LOWEST_PRECEDENCE - 2;'
check "aspect order: Bulkhead innermost (LOWEST - 1)"     $S6/bulkhead/configure/BulkheadConfigurationProperties.java 'bulkheadAspectOrder = Ordered.LOWEST_PRECEDENCE - 1;'
check "resilience4j-spring-boot4 module (Boot 4.0)"       $R/libraries.gradle "springBoot4Version = '4\."
check "...uses spring-boot-starter-aspectj"               $R/libraries.gradle 'spring-boot-starter-aspectj'
check "...properties under resilience4j.circuitbreaker"   $R/resilience4j-spring-boot4/src/main/java/io/github/resilience4j/springboot/circuitbreaker/autoconfigure/CircuitBreakerProperties.java 'prefix = "resilience4j.circuitbreaker"'
FC=$R/resilience4j-framework-common/src/main/java/io/github/resilience4j/common
check "YAML: retry-exception-predicate"                   $FC/retry/configuration/CommonRetryConfigurationProperties.java 'Class<\? extends Predicate<Throwable>> retryExceptionPredicate;'
check "YAML: exponential + randomized wait combine"       $FC/retry/configuration/CommonRetryConfigurationProperties.java 'ofExponentialRandomBackoff\(waitDuration, backoffMultiplier, randomizedWaitFactor, maxWaitDuration\)'
check "YAML: register-health-indicator"                   $FC/circuitbreaker/configuration/CommonCircuitBreakerConfigurationProperties.java 'private Boolean registerHealthIndicator;'
check "the BOM lists resilience4j-spring-boot3..."        $R/resilience4j-bom/build.gradle 'api project\(":resilience4j-spring-boot3"\)'
if grep -q 'resilience4j-spring-boot4' $R/resilience4j-bom/build.gradle; then
  printf 'MISSING  %s\n' "...but not -spring-boot4 (it does now: drop the explicit version in pom.xml)"; fail=$((fail + 1))
else
  printf 'OK       %s\n' "...but not -spring-boot4 (so pom.xml gives its version)"
fi
check "Spring Cloud CircuitBreaker 5.0.2 manages 2.3.0"   $SCCB/spring-cloud-circuitbreaker-dependencies/pom.xml '<resilience4j.version>2.3.0</resilience4j.version>'

echo "-- Spring Framework 7.0.9"
check "@Retryable: maxRetries 3"                          $RA/annotation/Retryable.java 'long maxRetries\(\) default 3;'
check "RetryPolicy: delay 1000 ms, jitter 0, multiplier 1" $SC/core/retry/RetryPolicy.java 'DEFAULT_MULTIPLIER = 1.0;'
check "includes/excludes match causes too"                $SC/core/retry/DefaultRetryPolicy.java 'exceptionFilter.match\(throwable, true\)'
check "jitter: ± jitter, never below the initial delay"   $SC/util/backoff/ExponentialBackOff.java 'Math.max\(interval - applicableJitter, initialInterval\)'
check "RetryTemplate sleeps between attempts"             $SC/core/retry/RetryTemplate.java 'Thread.sleep\(sleepTime\);'
check "@ConcurrencyLimit: policy BLOCK by default"        $RA/annotation/ConcurrencyLimit.java 'ThrottlePolicy policy\(\) default ThrottlePolicy.BLOCK;'
check "BLOCK waits without a timeout"                     $SC/util/ConcurrencyThrottleSupport.java 'this.concurrencyCondition.await\(\);'
check "REJECT throws InvocationRejectedException"         $RA/annotation/ConcurrencyLimitBeanPostProcessor.java 'throw new InvocationRejectedException'

echo
[ $fail -eq 0 ] && echo "all checks passed" || { echo "$fail checks failed"; exit 1; }
