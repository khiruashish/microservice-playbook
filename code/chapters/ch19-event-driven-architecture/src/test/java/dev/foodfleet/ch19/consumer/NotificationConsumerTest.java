package dev.foodfleet.ch19.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch19.bus.Events.MenuItemChanged;
import dev.foodfleet.ch19.catalog.Catalog;
import dev.foodfleet.ch19.sim.EventLoop;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NotificationConsumerTest {

    private final EventLoop loop = new EventLoop();

    private Catalog catalog(long outageStart, long outageEnd) {
        return new Catalog(loop, new Random(1), 10, outageStart, outageEnd);
    }

    @Test
    void everyEventCostsACallBackWhenNaive() {
        Catalog catalog = catalog(Long.MAX_VALUE, Long.MAX_VALUE);
        NotificationConsumer consumer = new NotificationConsumer(loop, catalog, new Replica(i -> { }), false, new Random(1));

        for (int i = 0; i < 5; i++) {
            consumer.accept(new MenuItemChanged("item-3"));
        }
        loop.runUntil(EventLoop.seconds(1));

        assertThat(catalog.apiCalls()).isEqualTo(5L);
    }

    @Test
    void coalescingFetchesAnItemAtMostTwiceForABurst() {
        Catalog catalog = catalog(Long.MAX_VALUE, Long.MAX_VALUE);
        NotificationConsumer consumer = new NotificationConsumer(loop, catalog, new Replica(i -> { }), true, new Random(1));

        for (int i = 0; i < 5; i++) {
            consumer.accept(new MenuItemChanged("item-3"));      // all while the first fetch is in flight
        }
        loop.runUntil(EventLoop.seconds(1));

        assertThat(catalog.apiCalls()).isEqualTo(2L);             // the fetch, plus one for "changed meanwhile"
    }

    @Test
    void whileTheCatalogIsDownTheReplicaCannotLearnAnything() {
        Catalog catalog = catalog(0, EventLoop.seconds(10));
        Replica replica = new Replica(i -> { });
        catalog.all().forEach(replica::apply);
        long before = replica.get("item-3").orElseThrow().version();
        NotificationConsumer consumer = new NotificationConsumer(loop, catalog, replica, true, new Random(1));

        catalog.change();                                        // may or may not hit item-3; force one:
        while (catalog.truth(3).version() == before) {
            catalog.change();
        }
        consumer.accept(new MenuItemChanged("item-3"));
        loop.runUntil(EventLoop.seconds(9));
        assertThat(replica.get("item-3").orElseThrow().version()).isEqualTo(before);   // still stale

        loop.runUntil(EventLoop.seconds(60));                    // catalog back: a retry succeeds
        assertThat(replica.get("item-3").orElseThrow().version()).isEqualTo(catalog.truth(3).version());
        assertThat(consumer.retries()).isPositive();
    }
}
