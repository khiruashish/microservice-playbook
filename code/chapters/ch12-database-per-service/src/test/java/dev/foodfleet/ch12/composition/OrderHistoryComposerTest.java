package dev.foodfleet.ch12.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.foodfleet.ch12.composition.Ports.DeliveryInfo;
import dev.foodfleet.ch12.composition.Ports.OrderRow;
import dev.foodfleet.ch12.composition.Ports.RestaurantInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OrderHistoryComposerTest {

    static final Instant T0 = Instant.parse("2026-09-30T14:00:00Z");

    final AtomicInteger calls = new AtomicInteger();
    final List<OrderRow> orders = IntStream.range(0, 20)
            .mapToObj(i -> new OrderRow("o-" + i, "rest-" + (i % 4), T0.minusSeconds(i * 3600L), 50_000 + i, "DELIVERED"))
            .toList();

    Ports.Ordering ordering = (customer, limit) -> {
        calls.incrementAndGet();
        return orders.subList(0, Math.min(limit, orders.size()));
    };
    Ports.Catalog catalog = ids -> {
        calls.incrementAndGet();
        return ids.stream().filter(id -> !id.equals("rest-3"))           // rest-3 was deleted from the catalog
                .collect(Collectors.toMap(Function.identity(), id -> new RestaurantInfo(id, "Name " + id, "Koramangala")));
    };
    Ports.Delivery delivery = ids -> {
        calls.incrementAndGet();
        return ids.stream().collect(Collectors.toMap(Function.identity(), id -> new DeliveryInfo(id, "Ravi", T0)));
    };

    @Test
    void threeCallsWhateverThePageSize() {
        OrderHistory history = new OrderHistoryComposer(ordering, catalog, delivery, Duration.ofSeconds(1)).history("c-42", 20);

        assertThat(calls).hasValue(3);
        assertThat(history.entries()).hasSize(20);
        assertThat(history.entries()).extracting(OrderHistoryEntry::orderId).startsWith("o-0", "o-1", "o-2");
        assertThat(history.entries().get(1).restaurantName()).isEqualTo("Name rest-1");
        assertThat(history.entries().get(3).restaurantName()).as("missing in catalog, not a failure").isNull();
        assertThat(history.degraded()).isEmpty();
    }

    @Test
    void theNaiveLoopMakesOnePlusTwoNCalls() {
        new NaiveOrderHistoryComposer(ordering, catalog, delivery).history("c-42", 20);

        assertThat(calls).hasValue(1 + 2 * 20);
    }

    @Test
    void aFailingEnrichmentDegradesInsteadOfFailingThePage() {
        Ports.Catalog down = ids -> {
            throw new IllegalStateException("catalog-service unavailable");
        };

        OrderHistory history = new OrderHistoryComposer(ordering, down, delivery, Duration.ofSeconds(1)).history("c-42", 5);

        assertThat(history.entries()).hasSize(5).allSatisfy(e -> {
            assertThat(e.restaurantName()).isNull();
            assertThat(e.riderName()).isEqualTo("Ravi");
        });
        assertThat(history.degraded()).containsExactly("catalog");
    }

    @Test
    void aSlowEnrichmentIsCutOffAtTheBudget() {
        Ports.Delivery slow = ids -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of();
        };

        long start = System.nanoTime();
        OrderHistory history = new OrderHistoryComposer(ordering, catalog, slow, Duration.ofMillis(200)).history("c-42", 5);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_500));
        assertThat(history.degraded()).containsExactly("delivery");
    }

    @Test
    void batchingSendsDistinctIdsOnly() {
        List<Collection<String>> seen = new ArrayList<>();
        Ports.Catalog recording = ids -> {
            seen.add(List.copyOf(ids));
            return Map.of();
        };

        new OrderHistoryComposer(ordering, recording, delivery, Duration.ofSeconds(1)).history("c-42", 20);

        assertThat(seen).singleElement().satisfies(ids -> assertThat(ids).containsExactly("rest-0", "rest-1", "rest-2", "rest-3"));
    }

    @Test
    void latencyModel() {
        LatencyModel model = new LatencyModel(40, 30, 0.5);

        assertThat(model.naiveMs(20)).isCloseTo(1_260, within(0.01));
        assertThat(model.batchedParallelMs(20)).isCloseTo(80, within(0.01));
    }
}
