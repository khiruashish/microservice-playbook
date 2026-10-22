package dev.foodfleet.ch44.hedge;

import io.github.resilience4j.hedge.Hedge;
import io.github.resilience4j.hedge.HedgeConfig;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Hedged reads with Resilience4j's Hedge: if the first call hasn't answered after {@code delay} (FoodFleet: the
 * dependency's p95), a second identical call starts and the first answer wins. Resilience4j hedges every slow call;
 * near capacity that extra load makes more calls slow, which makes more hedges (measured in chapter 44). So hedges
 * here are paid from a budget: each read earns {@code ratio} of a hedge, a hedge costs one, at most 10 are saved up.
 * With the budget spent, the read simply waits for its first call. Idempotent reads only, inside the caller's deadline.
 */
public final class HedgedReads {

    private static final long SCALE = 1000;

    private final Hedge hedge;
    private final Executor executor;
    private final Duration deadline;
    private final long earnPerRead;
    private final AtomicLong balance = new AtomicLong();
    private final AtomicLong hedgesSkipped = new AtomicLong();

    /** @param ratio hedges allowed per read, e.g. 0.05; 1.0 hedges every slow read (Resilience4j's behaviour) */
    public HedgedReads(String name, Duration delay, Duration deadline, double ratio, Executor executor) {
        this.hedge = Hedge.of(name, HedgeConfig.custom().preconfiguredDuration(delay).build());
        this.executor = executor;
        this.deadline = deadline;
        this.earnPerRead = Math.round(ratio * SCALE);
    }

    public <T> CompletableFuture<T> read(Supplier<T> call) {
        balance.accumulateAndGet(earnPerRead, (b, e) -> Math.min(10 * SCALE, b + e));
        AtomicBoolean primary = new AtomicBoolean(true);
        Supplier<CompletionStage<T>> hedged = hedge.decorateCompletionStage(() -> {
            if (primary.getAndSet(false) || spend()) {
                return CompletableFuture.supplyAsync(call, executor);
            }
            hedgesSkipped.incrementAndGet();
            return new CompletableFuture<T>();   // no hedge: the primary's answer (or the deadline) decides
        });
        return hedged.get().toCompletableFuture().orTimeout(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean spend() {
        while (true) {
            long b = balance.get();
            if (b < SCALE) {
                return false;
            }
            if (balance.compareAndSet(b, b - SCALE)) {
                return true;
            }
        }
    }

    public long hedgesSkipped() {
        return hedgesSkipped.get();
    }

    public Hedge.Metrics metrics() {
        return hedge.getMetrics();
    }
}
