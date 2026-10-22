package dev.foodfleet.ch08.eta.client;

import dev.foodfleet.ch08.eta.config.EtaProperties;
import dev.foodfleet.ch08.eta.domain.EtaCalculator;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.Eta;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.RiderLeg;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fans out to kitchen and dispatch in parallel on virtual threads, so the ETA costs
 * {@code max(kitchen, dispatch)} instead of {@code kitchen + dispatch}.
 *
 * <p>Plain blocking code: no reactive types, no callbacks. A virtual thread that blocks on a socket
 * read unmounts from its carrier thread, so blocking is cheap. One executor per request is fine:
 * virtual threads are not pooled.
 */
@Service
public class EtaService {

    private static final Logger log = LoggerFactory.getLogger(EtaService.class);

    private final KitchenClient kitchen;
    private final DispatchGateway dispatch;
    private final EtaProperties properties;
    private final Clock clock;
    private final EtaCalculator calculator = new EtaCalculator();

    EtaService(KitchenClient kitchen, DispatchGateway dispatch, EtaProperties properties, Clock clock) {
        this.kitchen = kitchen;
        this.dispatch = dispatch;
        this.properties = properties;
        this.clock = clock;
    }

    public Eta etaFor(String orderId) {
        long deadline = System.nanoTime() + properties.budget().toNanos();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> prep = executor.submit(() -> kitchen.prep(orderId).minutesRemaining());
            Future<Optional<RiderLeg>> rider = executor.submit(() -> dispatch.riderFor(orderId));

            int prepMinutes;
            try {
                prepMinutes = prep.get(remaining(deadline), TimeUnit.NANOSECONDS);
            } catch (ExecutionException | TimeoutException e) {
                rider.cancel(true);
                prep.cancel(true);
                throw new KitchenUnavailableException(orderId, e instanceof ExecutionException ? e.getCause() : e);
            }
            RiderLeg leg = riderOrNull(orderId, rider, deadline);
            return calculator.calculate(prepMinutes, leg, properties.fallbackTravelMinutes(), clock.instant());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KitchenUnavailableException(orderId, e);
        }
    }

    /** Dispatch is optional: on failure or timeout, degrade to the zone's average travel time. */
    private @Nullable RiderLeg riderOrNull(String orderId, Future<Optional<RiderLeg>> rider, long deadline)
            throws InterruptedException {
        try {
            return rider.get(remaining(deadline), TimeUnit.NANOSECONDS).orElse(null);
        } catch (ExecutionException | TimeoutException e) {
            rider.cancel(true);
            log.warn("Dispatch unavailable for order {}; using fallback travel time: {}", orderId, e.toString());
            return null;
        }
    }

    private static long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }
}
