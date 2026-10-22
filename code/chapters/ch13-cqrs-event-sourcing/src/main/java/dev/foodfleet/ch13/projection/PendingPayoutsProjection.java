package dev.foodfleet.ch13.projection;

import dev.foodfleet.ch13.eventstore.StoredEvent;
import dev.foodfleet.ch13.wallet.WalletEvent;
import dev.foodfleet.ch13.wallet.WalletEventCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ops dashboard: payouts requested but not yet completed or failed, oldest first. */
public final class PendingPayoutsProjection implements Projection {

    public record Pending(String payoutId, String riderId, long paise, Instant requestedAt) {
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "pending-payouts";
    }

    @Override
    public void handle(StoredEvent stored) {
        if (!stored.streamId().startsWith("wallet-")) {
            return;
        }
        String riderId = stored.streamId().substring("wallet-".length());
        switch (WalletEventCodec.decode(stored)) {
            case WalletEvent.PayoutRequested e -> pending.put(e.payoutId(), new Pending(e.payoutId(), riderId, e.paise(), e.occurredAt()));
            case WalletEvent.PayoutCompleted e -> pending.remove(e.payoutId());
            case WalletEvent.PayoutFailed e -> pending.remove(e.payoutId());
            default -> {
            }
        }
    }

    @Override
    public void reset() {
        pending.clear();
    }

    public List<Pending> olderThan(Duration age, Instant now) {
        return pending.values().stream()
                .filter(p -> p.requestedAt().plus(age).isBefore(now))
                .sorted(Comparator.comparing(Pending::requestedAt))
                .toList();
    }

    public int count() {
        return pending.size();
    }
}
