package dev.foodfleet.ch52.shutdown;

import dev.foodfleet.ch52.availability.Availability;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What a pod should do between SIGTERM and exiting, in order:
 * <ol>
 *   <li>Report readiness REFUSING_TRAFFIC, so the readiness probe fails.</li>
 *   <li>Keep serving for {@code drain}: Kubernetes removes the pod from Service endpoints asynchronously, and
 *       kube-proxies and load balancers keep sending new requests until they catch up.</li>
 *   <li>Stop accepting connections and let in-flight requests finish, for at most {@code timeout}
 *       (Spring Boot: {@code server.shutdown=graceful}, {@code spring.lifecycle.timeout-per-shutdown-phase}).</li>
 * </ol>
 * Spring Boot does steps 1 and 3; step 2 is a {@code preStop} sleep in the pod spec or a delay here.
 */
public final class GracefulShutdown {

    /** The server being shut down. */
    public interface Server {
        /** Stops accepting new connections. */
        void stopAccepting();

        /** Closes everything, including connections still open. */
        void close();
    }

    private final Availability availability;
    private final Duration drain;
    private final Duration timeout;
    private final AtomicInteger inFlight = new AtomicInteger();

    public GracefulShutdown(Availability availability, Duration drain, Duration timeout) {
        this.availability = availability;
        this.drain = drain;
        this.timeout = timeout;
    }

    /** Wraps request handling so in-flight requests are counted. */
    public void track(Runnable request) {
        inFlight.incrementAndGet();
        try {
            request.run();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public int inFlight() {
        return inFlight.get();
    }

    /** Runs the three steps; returns the number of requests still in flight when it gave up (0 when all finished). */
    public int shutdown(Server server) throws InterruptedException {
        availability.readiness(Availability.Readiness.REFUSING_TRAFFIC);
        Thread.sleep(drain.toMillis());
        server.stopAccepting();
        long end = System.nanoTime() + timeout.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        int left = inFlight.get();
        server.close();
        return left;
    }
}
