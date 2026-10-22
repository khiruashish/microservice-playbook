package dev.foodfleet.ch13.wallet;

import dev.foodfleet.ch13.eventstore.EventStore;
import dev.foodfleet.ch13.eventstore.StoredEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads a wallet from its latest snapshot plus the events after it, and saves new events with an
 * expected version. Snapshots are a cache: deleting them all changes nothing but load time.
 */
public final class WalletRepository {

    private final EventStore store;
    private final int snapshotEvery;
    private final Map<String, RiderWallet.Snapshot> snapshots = new ConcurrentHashMap<>();
    private long eventsReadOnLastLoad;

    public WalletRepository(EventStore store, int snapshotEvery) {
        this.store = store;
        this.snapshotEvery = snapshotEvery;
    }

    public static String streamId(String riderId) {
        return "wallet-" + riderId;
    }

    public RiderWallet load(String riderId) {
        RiderWallet.Snapshot snapshot = snapshots.get(riderId);
        RiderWallet wallet = snapshot == null ? RiderWallet.empty() : RiderWallet.fromSnapshot(snapshot);
        List<StoredEvent> tail = store.readStream(streamId(riderId), wallet.version());
        tail.forEach(e -> wallet.apply(WalletEventCodec.decode(e)));
        eventsReadOnLastLoad = tail.size();
        return wallet;
    }

    /** Appends with optimistic concurrency, applies the events, snapshots every N versions. */
    public RiderWallet save(String riderId, RiderWallet wallet, List<WalletEvent> newEvents) {
        if (newEvents.isEmpty()) {
            return wallet;
        }
        long before = wallet.version();
        store.append(streamId(riderId), before, newEvents.stream().map(WalletEventCodec::encode).toList());
        newEvents.forEach(wallet::apply);
        if (wallet.version() / snapshotEvery > before / snapshotEvery) {
            snapshots.put(riderId, wallet.snapshot());
        }
        return wallet;
    }

    /** Temporal query: the wallet as it was at {@code asOf}, by replaying only earlier events. */
    public RiderWallet asOf(String riderId, Instant asOf) {
        RiderWallet wallet = RiderWallet.empty();
        for (StoredEvent stored : store.readStream(streamId(riderId), 0)) {
            WalletEvent e = WalletEventCodec.decode(stored);
            if (e.occurredAt().isAfter(asOf)) {
                break;
            }
            wallet.apply(e);
        }
        return wallet;
    }

    public long eventsReadOnLastLoad() {
        return eventsReadOnLastLoad;
    }
}
