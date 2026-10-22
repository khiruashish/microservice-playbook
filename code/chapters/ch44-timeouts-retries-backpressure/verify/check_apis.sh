#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 44 relies on against the sources of Netflix concurrency-limits 0.5.4,
# Resilience4j 2.4.0 (hedge) and the JDK (from its lib/src.zip, if the JDK ships one).
# Usage: ./check_apis.sh <sources dir> [<JDK home>]
set -uo pipefail
SRC=$1 JDK=${2:-}
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
N=$SRC/ncl/concurrency-limits-core/src/main/java/com/netflix/concurrency/limits
H=$SRC/r4j/resilience4j-hedge/src/main/java/io/github/resilience4j/hedge
C=$SRC/r4j/resilience4j-core/src/main/java/io/github/resilience4j/core

echo "-- Netflix concurrency-limits 0.5.4"
check "a limiter's default limit is Vegas"                    $N/limiter/AbstractLimiter.java 'private Limit limit = VegasLimit.newDefault\(\);'
check "Vegas: maxConcurrency 1000"                           $N/limit/VegasLimit.java 'private int maxConcurrency = 1000;'
check "Gradient2: minLimit 20"                               $N/limit/Gradient2Limit.java 'private int minLimit = 20;'
check "Gradient2: maxConcurrency 200"                        $N/limit/Gradient2Limit.java 'private int maxConcurrency = 200;'
check "Gradient2: queue size 4"                              $N/limit/Gradient2Limit.java 'queueSize = concurrency -> 4;'
check "Gradient2: rttTolerance 1.5"                          $N/limit/Gradient2Limit.java 'private double rttTolerance = 1.5;'
check "AIMD: initial and min limit 20, max 200"              $N/limit/AIMDLimit.java 'private int maxLimit = 200;'
check "AIMD: backs off to 90%"                               $N/limit/AIMDLimit.java 'private double backoffRatio = 0.9;'
check "AIMD: 'slow' means over 5 s by default"               $N/limit/AIMDLimit.java 'DEFAULT_TIMEOUT = TimeUnit.SECONDS.toNanos\(5\);'
check "AIMD: shrinks on a drop or a slow call"               $N/limit/AIMDLimit.java 'if \(didDrop \|\| rtt > timeout\)'
check "AIMD: grows only when at least half the limit is used" $N/limit/AIMDLimit.java 'inflight \* 2 >= currentLimit'
check "partitions apply only at the global limit"            $N/limiter/AbstractPartitionedLimiter.java 'if \(getInflight\(\) >= getLimit\(\)\)'
check "partitioned limiter is abstract in core"              $N/limiter/AbstractPartitionedLimiter.java 'public abstract class AbstractPartitionedLimiter'
check "BlockingLimiter waits in synchronized + Object.wait"  $N/limiter/BlockingLimiter.java 'lock.wait\(timeout\);'
check "LifoBlockingLimiter: backlog 100, 1 s"                $N/limiter/LifoBlockingLimiter.java 'private Long fixedBacklogTimeoutMillis = 1_000L;'

echo "-- Resilience4j 2.4.0 hedge"
check "Hedge: a fixed delay, or average + a margin"          $H/HedgeConfig.java 'public Builder averagePlusPercentageDuration'
check "...the adaptive delay is the mean, not a percentile"  $H/internal/AverageDurationSupplier.java 'getAverageDuration\(\)'
check "Hedge: every slow call is hedged (no budget)"         $H/internal/HedgeImpl.java 'schedule\(\(\) -> timedCompletable.complete\(null\)'
check "Hedge: own scheduler with 10 threads per instance"    $H/HedgeConfig.java 'private int concurrentHedges = 10;'
check "...non-daemon threads, never shut down"               $C/NamingThreadFactory.java 'thread.setDaemon\(false\);'
check "the losing call is not interrupted"                   $H/internal/HedgeImpl.java 'supplied.cancel\(false\);'

echo "-- JDK"
if [ -n "$JDK" ] && [ -f "$JDK/lib/src.zip" ]; then
  T=$(mktemp -d)
  unzip -q -o "$JDK/lib/src.zip" java.base/java/util/concurrent/Flow.java java.base/java/util/concurrent/SubmissionPublisher.java -d "$T"
  check "Flow: default buffer 256"                           "$T/java.base/java/util/concurrent/Flow.java" 'DEFAULT_BUFFER_SIZE = 256;'
  check "SubmissionPublisher.submit blocks"                  "$T/java.base/java/util/concurrent/SubmissionPublisher.java" 'blocking uninterruptibly'
  check "SubmissionPublisher.offer with timeout and onDrop"  "$T/java.base/java/util/concurrent/SubmissionPublisher.java" 'public int offer\(T item, long timeout, TimeUnit unit,'
  rm -rf "$T"
else
  echo "skipped: no lib/src.zip in '${JDK}'"
fi

echo
[ $fail -eq 0 ] && echo "all checks passed" || { echo "$fail checks failed"; exit 1; }
