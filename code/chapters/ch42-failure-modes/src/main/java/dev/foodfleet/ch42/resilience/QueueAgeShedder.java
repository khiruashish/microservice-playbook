package dev.foodfleet.ch42.resilience;

/**
 * Server-side admission when work is picked up from the queue: refuse it cheaply if it has waited longer than
 * {@code maxQueueMillis} or its deadline has passed. Under overload a FIFO queue fills with requests whose callers
 * have timed out; serving them uses the whole capacity on work nobody receives. Dropping stale work first keeps the
 * capacity for requests that can still succeed.
 */
public final class QueueAgeShedder {

    public enum Verdict { SERVE, SHED_STALE, SHED_EXPIRED }

    private final long maxQueueMillis;

    public QueueAgeShedder(long maxQueueMillis) {
        this.maxQueueMillis = maxQueueMillis;
    }

    public Verdict admit(long enqueuedAtMillis, Deadline deadline, long nowMillis) {
        if (deadline != null && deadline.expired(nowMillis)) {
            return Verdict.SHED_EXPIRED;
        }
        if (nowMillis - enqueuedAtMillis > maxQueueMillis) {
            return Verdict.SHED_STALE;
        }
        return Verdict.SERVE;
    }
}
