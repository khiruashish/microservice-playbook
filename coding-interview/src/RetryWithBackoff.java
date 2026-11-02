import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Problem: retry a remote call with exponential backoff + full jitter, only for retryable errors, within a retry budget. */
public class RetryWithBackoff {

    static final class TransientException extends RuntimeException { TransientException(String m) { super(m); } }
    static final class PermanentException extends RuntimeException { PermanentException(String m) { super(m); } }

    interface Sleeper { void sleep(long millis); }

    /** Token-bucket style retry budget: retries may be at most ~ratio of recent requests (stops retry storms). */
    static final class RetryBudget {
        private double tokens;
        private final double ratio, max;
        RetryBudget(double ratio, double max) { this.ratio = ratio; this.max = max; this.tokens = max; }
        synchronized void onRequest() { tokens = Math.min(max, tokens + ratio); }
        synchronized boolean tryAcquire() { if (tokens >= 1) { tokens -= 1; return true; } return false; }
    }

    static final class Retrier {
        private final int maxAttempts;
        private final long baseMillis, capMillis;
        private final Predicate<Throwable> retryable;
        private final Random random;
        private final Sleeper sleeper;
        private final RetryBudget budget;
        final List<Long> delays = new ArrayList<>();

        Retrier(int maxAttempts, long baseMillis, long capMillis, Predicate<Throwable> retryable, Random random, Sleeper sleeper, RetryBudget budget) {
            this.maxAttempts = maxAttempts; this.baseMillis = baseMillis; this.capMillis = capMillis;
            this.retryable = retryable; this.random = random; this.sleeper = sleeper; this.budget = budget;
        }

        <T> T call(Supplier<T> op) {
            budget.onRequest();
            for (int attempt = 1; ; attempt++) {
                try {
                    return op.get();
                } catch (RuntimeException e) {
                    if (!retryable.test(e) || attempt == maxAttempts || !budget.tryAcquire()) throw e;
                    long ceiling = Math.min(capMillis, baseMillis << (attempt - 1));   // base * 2^(attempt-1), capped
                    long delay = (long) (random.nextDouble() * ceiling);              // FULL jitter: uniform in [0, ceiling)
                    delays.add(delay);
                    sleeper.sleep(delay);
                }
            }
        }
    }

    public static void main(String[] args) {
        List<Long> slept = new ArrayList<>();
        Retrier retrier = new Retrier(5, 100, 2_000, e -> e instanceof TransientException, new Random(42), slept::add, new RetryBudget(0.2, 10));

        int[] calls = {0};
        String result = retrier.call(() -> { if (++calls[0] < 4) throw new TransientException("503"); return "OK after " + calls[0] + " calls"; });
        System.out.println(result + ", backoff delays (ms) = " + retrier.delays);
        check(calls[0] == 4 && retrier.delays.size() == 3, "3 retries then success");
        check(retrier.delays.get(0) < 100 && retrier.delays.get(1) < 200 && retrier.delays.get(2) < 400, "jitter stays under the exponential ceiling");

        calls[0] = 0;
        try { retrier.call(() -> { calls[0]++; throw new PermanentException("400 bad request"); }); }
        catch (PermanentException e) { System.out.println("permanent error not retried: calls=" + calls[0] + " (" + e.getMessage() + ")"); }
        check(calls[0] == 1, "no retry on 4xx");

        // Retry storm: 50 requests all failing. The budget caps total retries.
        Retrier stormy = new Retrier(5, 1, 10, e -> true, new Random(7), ms -> { }, new RetryBudget(0.1, 5));
        int[] total = {0};
        for (int i = 0; i < 50; i++) { try { stormy.call(() -> { total[0]++; throw new TransientException("down"); }); } catch (TransientException ignored) { } }
        System.out.println("50 failing requests -> " + total[0] + " total calls (without a budget: 250)");
        check(total[0] < 70, "retry budget prevents amplification");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
