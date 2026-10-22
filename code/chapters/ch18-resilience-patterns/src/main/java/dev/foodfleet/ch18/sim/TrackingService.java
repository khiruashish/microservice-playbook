package dev.foodfleet.ch18.sim;

import dev.foodfleet.ch18.resilience.Call;
import dev.foodfleet.ch18.resilience.Outcome;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * tracking-service: a fixed pool of request workers (like Tomcat's 200 threads, scaled down) with a
 * bounded accept queue. Two endpoints share the pool:
 * <ul>
 *   <li>{@code GET /orders/{id}/status}: 5 ms of local work, no dependencies;</li>
 *   <li>{@code GET /orders/{id}/eta}: 2 ms of local work plus a call to the maps provider, through
 *       whatever resilience decorators the scenario configures.</li>
 * </ul>
 * A response that arrives after the app's 2 s client timeout counts as a failure: the customer
 * has already seen a spinner turn into an error.
 */
public final class TrackingService {

    public enum Endpoint { STATUS, ETA }

    public record Request(Endpoint endpoint, long arrivedAt) {
    }

    public interface Recorder {
        void record(Request request, Outcome outcome, long finishedAt);
    }

    public static final long CLIENT_TIMEOUT = EventLoop.seconds(2);

    private final EventLoop loop;
    private final Call eta;
    private final Recorder recorder;
    private final int queueLimit;
    private final Deque<Request> queue = new ArrayDeque<>();
    private int idleWorkers;
    private long rejected;

    public TrackingService(EventLoop loop, Call eta, Recorder recorder, int workers, int queueLimit) {
        this.loop = loop;
        this.eta = eta;
        this.recorder = recorder;
        this.idleWorkers = workers;
        this.queueLimit = queueLimit;
    }

    public void arrive(Endpoint endpoint) {
        Request request = new Request(endpoint, loop.now());
        if (idleWorkers > 0) {
            work(request);
        } else if (queue.size() < queueLimit) {
            queue.addLast(request);
        } else {
            rejected++;
            recorder.record(request, Outcome.UNAVAILABLE, loop.now());   // 503: server busy
        }
    }

    private void work(Request request) {
        idleWorkers--;
        if (request.endpoint() == Endpoint.STATUS) {
            loop.after(EventLoop.ms(5), () -> finish(request, Outcome.SUCCESS));
        } else {
            loop.after(EventLoop.ms(2), () -> eta.start(outcome -> finish(request, outcome)));
        }
    }

    private void finish(Request request, Outcome outcome) {
        idleWorkers++;
        boolean tooLate = loop.now() - request.arrivedAt() > CLIENT_TIMEOUT;
        recorder.record(request, tooLate ? Outcome.TIMEOUT : outcome, loop.now());
        if (!queue.isEmpty()) {
            work(queue.pollFirst());
        }
    }

    public long rejected() {
        return rejected;
    }
}
