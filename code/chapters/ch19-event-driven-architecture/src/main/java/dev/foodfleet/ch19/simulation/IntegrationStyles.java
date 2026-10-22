package dev.foodfleet.ch19.simulation;

import dev.foodfleet.ch19.bus.EventBus;
import dev.foodfleet.ch19.bus.Events.MenuItemChanged;
import dev.foodfleet.ch19.bus.Events.MenuItemUpdated;
import dev.foodfleet.ch19.catalog.Catalog;
import dev.foodfleet.ch19.catalog.MenuItem;
import dev.foodfleet.ch19.consumer.NotificationConsumer;
import dev.foodfleet.ch19.consumer.Replica;
import dev.foodfleet.ch19.consumer.StateTransferConsumer;
import dev.foodfleet.ch19.sim.EventLoop;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Ten minutes of lunch rush. catalog-service owns 5,000 menu items and changes 50 of them a second
 * (items selling out and coming back, price changes). Three services need menu data: search,
 * recommendations and ordering. ordering-service takes 200 orders a second and must check the item
 * is available and use its current price. From t=4:00 to t=6:00 catalog-service's API is down.
 *
 * <p>Three integration styles (four rows), same seeded changes and orders:
 * <ul>
 *   <li><b>synchronous</b>: ordering calls catalog for every order; no events;</li>
 *   <li><b>event notification</b>: catalog publishes "item X changed"; each consumer calls back
 *       (naively, or with per-item coalescing and jittered backoff);</li>
 *   <li><b>event-carried state transfer</b>: catalog publishes the item's new state and version;
 *       consumers keep a local replica.</li>
 * </ul>
 */
public final class IntegrationStyles {

    static final long DURATION = EventLoop.seconds(600);
    static final long OUTAGE_START = EventLoop.seconds(240);
    static final long OUTAGE_END = EventLoop.seconds(360);
    static final int ITEMS = 5_000;
    static final int CONSUMERS = 3;

    public enum Style {
        SYNCHRONOUS("synchronous call per order"),
        NOTIFICATION_NAIVE("notification, naive callbacks"),
        NOTIFICATION("notification, coalesced + backoff"),
        STATE_TRANSFER("event-carried state transfer");

        public final String label;

        Style(String label) {
            this.label = label;
        }
    }

    public record Result(Style style, long orders, double ordersFailed, double soldOutAccepted,
                         double wrongPrice, double failedDuringOutage, double soldOutAcceptedDuringOutage,
                         long catalogCalls, long busMessages, long busBytes, double stalenessP50Ms,
                         double stalenessP99Ms, double stalenessMaxMs) {
    }

    /** Tracks, per item, when each change was made, to measure how long ordering's view lagged. */
    private static final class Staleness {
        private final Map<String, Deque<long[]>> pending = new HashMap<>();   // {version, changedAt}
        private final List<Long> lags = new ArrayList<>();
        private final EventLoop loop;

        Staleness(EventLoop loop) {
            this.loop = loop;
        }

        void changed(MenuItem item) {
            pending.computeIfAbsent(item.id(), k -> new ArrayDeque<>()).addLast(new long[] {item.version(), loop.now()});
        }

        void reflected(MenuItem item) {
            Deque<long[]> waiting = pending.get(item.id());
            while (waiting != null && !waiting.isEmpty() && waiting.peekFirst()[0] <= item.version()) {
                lags.add(loop.now() - waiting.pollFirst()[1]);
            }
        }

        double percentileMs(int p) {
            if (lags.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(lags);
            Collections.sort(sorted);
            return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1)) / 1_000.0;
        }
    }

    private static final class OrderStats {
        long orders, failed, soldOutAccepted, wrongPrice;
        long outageOrders, outageFailed, outageSoldOutAccepted;
    }

    public static Result run(Style style, long seed) {
        EventLoop loop = new EventLoop();
        Catalog catalog = new Catalog(loop, new Random(seed), ITEMS, OUTAGE_START, OUTAGE_END);
        Random busRandom = new Random(seed + 1);
        Random orderRandom = new Random(seed + 2);
        Random changeTiming = new Random(seed + 3);
        Staleness staleness = new Staleness(loop);
        OrderStats stats = new OrderStats();

        // ordering-service's replica reports what it applied; the other two consumers just keep up.
        Replica ordering = new Replica(staleness::reflected);
        List<Replica> replicas = List.of(ordering, new Replica(item -> { }), new Replica(item -> { }));
        catalog.all().forEach(item -> replicas.forEach(r -> r.apply(item)));         // bootstrapped at t=0

        EventBus<MenuItemChanged> thin = new EventBus<>(loop, busRandom);
        EventBus<MenuItemUpdated> fat = new EventBus<>(loop, busRandom);
        if (style == Style.NOTIFICATION || style == Style.NOTIFICATION_NAIVE) {
            Random retryRandom = new Random(seed + 4);
            replicas.forEach(r -> thin.subscribe(
                    new NotificationConsumer(loop, catalog, r, style == Style.NOTIFICATION, retryRandom)));
        } else if (style == Style.STATE_TRANSFER) {
            replicas.forEach(r -> fat.subscribe(new StateTransferConsumer(r)));
        }

        Consumer<MenuItem> publish = item -> {
            staleness.changed(item);
            if (style == Style.NOTIFICATION || style == Style.NOTIFICATION_NAIVE) {
                MenuItemChanged e = new MenuItemChanged(item.id());
                thin.publish(item.id(), e, e.json().length());
            } else if (style == Style.STATE_TRANSFER) {
                MenuItemUpdated e = new MenuItemUpdated(item);
                fat.publish(item.id(), e, e.json().length());
            } else {
                staleness.reflected(item);           // synchronous: every order reads the source itself
            }
        };
        poisson(loop, changeTiming, 50, () -> publish.accept(catalog.change()));
        poisson(loop, orderRandom, 200, () -> placeOrder(style, loop, catalog, ordering, orderRandom, stats));
        loop.runUntil(DURATION + EventLoop.seconds(30));

        long busMessages = thin.messages() + fat.messages();
        long busBytes = thin.bytes() + fat.bytes();
        return new Result(style, stats.orders, pct(stats.failed, stats.orders), pct(stats.soldOutAccepted, stats.orders),
                pct(stats.wrongPrice, stats.orders), pct(stats.outageFailed, stats.outageOrders),
                pct(stats.outageSoldOutAccepted, stats.outageOrders), catalog.apiCalls(), busMessages, busBytes,
                staleness.percentileMs(50), staleness.percentileMs(99), staleness.percentileMs(100));
    }

    /**
     * An order for a random item. It goes wrong if ordering accepts an item that is actually sold
     * out right now (the kitchen will reject it later: a compensation, chapter 14), or charges a
     * price that is no longer the current one. With the synchronous style it fails outright if
     * catalog can't answer.
     */
    private static void placeOrder(Style style, EventLoop loop, Catalog catalog, Replica ordering, Random random,
                                   OrderStats stats) {
        int index = random.nextInt(ITEMS);
        String itemId = "item-" + index;
        boolean outage = catalog.down();
        stats.orders++;
        if (outage) {
            stats.outageOrders++;
        }
        if (style == Style.SYNCHRONOUS) {
            catalog.get(itemId, answer -> {
                if (answer.isEmpty()) {
                    stats.failed++;
                    if (outage) {
                        stats.outageFailed++;
                    }
                } else {
                    judge(answer.get(), catalog.truth(index), outage, stats);
                }
            });
            return;
        }
        judge(ordering.get(itemId).orElseThrow(), catalog.truth(index), outage, stats);
    }

    private static void judge(MenuItem seen, MenuItem truth, boolean outage, OrderStats stats) {
        if (!seen.available()) {
            return;                                              // shown as sold out: no order placed
        }
        if (!truth.available()) {
            stats.soldOutAccepted++;
            if (outage) {
                stats.outageSoldOutAccepted++;
            }
        } else if (seen.pricePaise() != truth.pricePaise()) {
            stats.wrongPrice++;
        }
    }

    private static void poisson(EventLoop loop, Random random, double perSecond, Runnable action) {
        long[] next = {0};
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            action.run();
            next[0] += (long) (-Math.log(1 - random.nextDouble()) / perSecond * 1_000_000);
            if (next[0] < DURATION) {
                loop.at(next[0], tick[0]);
            }
        };
        loop.at(0, tick[0]);
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0 : 100.0 * part / total;
    }

    /** What a NEW consumer (say, a pricing-analytics service) must read to build its view. */
    public record Bootstrap(long apiCalls, long compactedMessages, long compactedBytes, long fullLogMessages,
                            long fullLogBytes) {
    }

    public static Bootstrap bootstrap(long seed) {
        EventLoop loop = new EventLoop();
        Catalog catalog = new Catalog(loop, new Random(seed), ITEMS, Long.MAX_VALUE, Long.MAX_VALUE);
        Random timing = new Random(seed + 3);
        long[] log = {0, 0};
        catalog.all().forEach(item -> {
            log[0]++;
            log[1] += new MenuItemUpdated(item).json().length();
        });
        poisson(loop, timing, 50, () -> {
            MenuItem changed = catalog.change();
            log[0]++;
            log[1] += new MenuItemUpdated(changed).json().length();
        });
        loop.runUntil(DURATION);
        long compactedBytes = catalog.all().stream().mapToLong(i -> new MenuItemUpdated(i).json().length()).sum();
        return new Bootstrap(ITEMS, ITEMS, compactedBytes, log[0], log[1]);
    }

    public static void main(String[] args) {
        System.out.println("10 min lunch rush: 5,000 items, 50 changes/s, 200 orders/s, 3 consumers;");
        System.out.println("catalog API down from 4:00 to 6:00.\n");
        System.out.printf("%-34s %8s %9s %8s %10s %10s %10s %9s %10s %10s%n", "style", "orders", "sold-out", "wrong",
                "failed in", "sold-out", "catalog", "bus", "staleness", "staleness");
        System.out.printf("%-34s %8s %9s %8s %10s %10s %10s %9s %10s %10s%n", "", "failed", "accepted", "price",
                "outage", "in outage", "API calls", "MB", "p50 ms", "p99 ms");
        for (Style style : Style.values()) {
            Result r = run(style, 19);
            boolean sync = style == Style.SYNCHRONOUS;          // reads the source: no copy to go stale
            System.out.printf("%-34s %7.2f%% %8.2f%% %7.2f%% %9.1f%% %9.2f%% %10d %9.2f %10s %10s%n", style.label,
                    r.ordersFailed(), r.soldOutAccepted(), r.wrongPrice(), r.failedDuringOutage(),
                    r.soldOutAcceptedDuringOutage(), r.catalogCalls(), r.busBytes() / 1e6,
                    sync ? "-" : String.format("%.0f", r.stalenessP50Ms()),
                    sync ? "-" : String.format("%.0f", r.stalenessP99Ms()));
        }
        Bootstrap b = bootstrap(19);
        System.out.println("\nA new consumer joins after 10 minutes and needs the whole menu:");
        System.out.printf("  call the catalog API:                %,7d requests%n", b.apiCalls());
        System.out.printf("  read a compacted topic (latest/key): %,7d messages, %.1f MB%n", b.compactedMessages(),
                b.compactedBytes() / 1e6);
        System.out.printf("  replay the full event log:           %,7d messages, %.1f MB%n", b.fullLogMessages(),
                b.fullLogBytes() / 1e6);
    }
}
