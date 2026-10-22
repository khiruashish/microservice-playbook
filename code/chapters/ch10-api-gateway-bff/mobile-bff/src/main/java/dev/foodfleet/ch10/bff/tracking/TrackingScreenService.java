package dev.foodfleet.ch10.bff.tracking;

import dev.foodfleet.ch10.bff.client.Downstream;
import dev.foodfleet.ch10.bff.client.Downstream.OrderSummary;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Aggregation for the tracking screen, in two phases:
 * <ol>
 *   <li>the order (required: without it there is no screen);</li>
 *   <li>ETA, restaurant and rider in parallel on virtual threads (each optional: the screen still
 *       renders, and says what's missing).</li>
 * </ol>
 * Latency ≈ order + max(eta, restaurant, rider), instead of the app making four sequential calls
 * over a mobile network.
 */
@Service
public class TrackingScreenService {

    static final Duration OPTIONAL_PARTS_BUDGET = Duration.ofMillis(600);

    private final Downstream.OrderingClient ordering;
    private final Downstream.EtaClient etas;
    private final Downstream.CatalogClient catalog;
    private final Downstream.DispatchClient dispatch;
    private final Clock clock;
    private final TrackingCardAssembler assembler = new TrackingCardAssembler();

    TrackingScreenService(Downstream.OrderingClient ordering, Downstream.EtaClient etas,
                          Downstream.CatalogClient catalog, Downstream.DispatchClient dispatch, Clock clock) {
        this.ordering = ordering;
        this.etas = etas;
        this.catalog = catalog;
        this.dispatch = dispatch;
        this.clock = clock;
    }

    public TrackingCard trackingCard(String orderId) {
        OrderSummary order = ordering.order(orderId);           // 404 / 5xx propagate: no order, no screen

        List<String> failed = new ArrayList<>();
        long deadline = System.nanoTime() + OPTIONAL_PARTS_BUDGET.toNanos();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Downstream.Eta> eta = executor.submit(() -> etas.eta(orderId));
            Future<Downstream.Restaurant> restaurant = executor.submit(() -> catalog.restaurant(order.restaurantId()));
            Future<Optional<Downstream.Rider>> rider = executor.submit(riderOrEmpty(orderId));

            return assembler.assemble(order,
                    optional("eta", eta, deadline, failed),
                    optional("restaurant", restaurant, deadline, failed),
                    optional("rider", rider, deadline, failed).flatMap(r -> r),
                    failed, clock.instant());
        }
    }

    /** No rider yet is a normal state (404), not a failure. */
    private Callable<Optional<Downstream.Rider>> riderOrEmpty(String orderId) {
        return () -> {
            try {
                return Optional.of(dispatch.rider(orderId));
            } catch (HttpClientErrorException.NotFound notAssignedYet) {
                return Optional.empty();
            }
        };
    }

    private static <T> Optional<T> optional(String part, Future<T> future, long deadline, List<String> failed) {
        try {
            return Optional.ofNullable(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            failed.add(part);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed.add(part);
            return Optional.empty();
        }
    }
}
