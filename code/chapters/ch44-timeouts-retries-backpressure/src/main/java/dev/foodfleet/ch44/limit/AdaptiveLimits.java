package dev.foodfleet.ch44.limit;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * FoodFleet's server-side admission control: an AIMD concurrency limit (Netflix concurrency-limits) that grows by one
 * while requests finish in time and shrinks by 10% when one takes longer than {@code dropAbove} (half the callers'
 * deadline) or is reported dropped. Measured against Vegas and Gradient2 in chapter 44: their defaults either
 * rejected work the service could do or happened to fit only because their floor equalled the worker count.
 */
public final class AdaptiveLimits {

    private AdaptiveLimits() {
    }

    public static AIMDLimit aimd(Duration dropAbove) {
        return AIMDLimit.newBuilder()
                .initialLimit(20)
                .minLimit(10)
                .maxLimit(200)
                .backoffRatio(0.9)
                .timeout(dropAbove.toNanos(), TimeUnit.NANOSECONDS)
                .build();
    }

    public static <C> Limiter<C> simple(String name, Duration dropAbove) {
        return SimpleLimiter.newBuilder().named(name).limit(aimd(dropAbove)).build();
    }

    /** One adaptive limit; when it is reached, critical requests keep 90% of it and sheddable ones 10%. */
    public static Limiter<Priority> byPriority(String name, Duration dropAbove) {
        return PartitionedLimiter.<Priority>newBuilder()
                .named(name)
                .limit(aimd(dropAbove))
                .partitionResolver(Priority::partition)
                .partition(Priority.CRITICAL.partition(), 0.9)
                .partition(Priority.SHEDDABLE.partition(), 0.1)
                .build();
    }
}
