package dev.foodfleet.ch60.startup;

import java.util.function.IntConsumer;

/**
 * Warming a JVM before it takes traffic: run the request path enough times for the JIT to compile it, then report
 * ready. The same loop is a training run: with {@code -XX:AOTCacheOutput} (JDK 25) or {@code -XX:ArchiveClassesAtExit}
 * it records what the application loads, links and profiles, so later starts begin there.
 */
public final class Warmup {

    public record Result(int iterations, long millis) {
    }

    private Warmup() {
    }

    /** Calls {@code request} until {@code iterations} are done or {@code budgetMillis} has passed, whichever is first. */
    public static Result run(IntConsumer request, int iterations, long budgetMillis) {
        long start = System.nanoTime();
        long deadline = start + budgetMillis * 1_000_000;
        int i = 0;
        while (i < iterations && System.nanoTime() < deadline) {
            request.accept(i++);
        }
        return new Result(i, (System.nanoTime() - start) / 1_000_000);
    }
}
