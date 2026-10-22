package dev.foodfleet.ch13.projection;

import dev.foodfleet.ch13.eventstore.StoredEvent;
import dev.foodfleet.ch13.wallet.WalletEvent;
import dev.foodfleet.ch13.wallet.WalletEventCodec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read model for the rider app's "today" card and the ops leaderboard: earnings per rider per
 * calendar day in India. Shaped for the query, not for the rules; nothing here could enforce a
 * payout limit, and nothing needs to.
 */
public final class DailyEarningsProjection implements Projection {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record Key(String riderId, LocalDate day) {
    }

    public record DayTotals(long earningsPaise, long tipsPaise, int deliveries) {
        static DayTotals plusAll(DayTotals a, DayTotals b) {
            return new DayTotals(a.earningsPaise + b.earningsPaise, a.tipsPaise + b.tipsPaise, a.deliveries + b.deliveries);
        }

        public long totalPaise() {
            return earningsPaise + tipsPaise;
        }
    }

    public record Leader(String riderId, long totalPaise, int deliveries) {
    }

    private final Map<Key, DayTotals> rows = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "daily-earnings";
    }

    @Override
    public void handle(StoredEvent stored) {
        if (!stored.streamId().startsWith("wallet-")) {
            return;
        }
        String riderId = stored.streamId().substring("wallet-".length());
        WalletEvent event = WalletEventCodec.decode(stored);
        LocalDate day = event.occurredAt().atZone(IST).toLocalDate();
        switch (event) {
            case WalletEvent.EarningCredited e -> rows.merge(new Key(riderId, day), new DayTotals(e.paise(), 0, 1), DayTotals::plusAll);
            case WalletEvent.TipCredited e -> rows.merge(new Key(riderId, day), new DayTotals(0, e.paise(), 0), DayTotals::plusAll);
            default -> {
                // payouts and adjustments don't change what was earned that day
            }
        }
    }

    @Override
    public void reset() {
        rows.clear();
    }

    public DayTotals day(String riderId, LocalDate day) {
        return rows.getOrDefault(new Key(riderId, day), new DayTotals(0, 0, 0));
    }

    public List<Leader> leaderboard(LocalDate day, int top) {
        return rows.entrySet().stream()
                .filter(e -> e.getKey().day().equals(day))
                .map(e -> new Leader(e.getKey().riderId(), e.getValue().totalPaise(), e.getValue().deliveries()))
                .sorted(Comparator.comparingLong(Leader::totalPaise).reversed().thenComparing(Leader::riderId))
                .limit(top)
                .toList();
    }
}
