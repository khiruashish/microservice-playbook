package dev.foodfleet.ch13;

import dev.foodfleet.ch13.eventstore.InMemoryEventStore;
import dev.foodfleet.ch13.projection.DailyEarningsProjection;
import dev.foodfleet.ch13.projection.PendingPayoutsProjection;
import dev.foodfleet.ch13.projection.ProjectionRunner;
import dev.foodfleet.ch13.wallet.RiderWallet;
import dev.foodfleet.ch13.wallet.WalletRepository;
import dev.foodfleet.ch13.wallet.WalletService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/** A rider's evening, event-sourced, printed step by step. Plain Java: {@code java ... WalletDemo}. */
public final class WalletDemo {

    public static void main(String[] args) {
        System.out.print(run());
    }

    public static String run() {
        StringBuilder out = new StringBuilder();
        InMemoryEventStore store = new InMemoryEventStore(Clock.fixed(Instant.parse("2026-10-01T16:00:00Z"), ZoneOffset.UTC));
        WalletRepository repository = new WalletRepository(store, 10);
        WalletService wallets = new WalletService(repository);
        Instant evening = Instant.parse("2026-10-01T13:30:00Z");          // 7:00 pm IST

        wallets.execute("ravi", w -> w.open("ravi", evening));
        for (int i = 1; i <= 12; i++) {
            Instant at = evening.plusSeconds(i * 900L);
            String order = "o-" + i;
            wallets.execute("ravi", w -> w.creditEarning(order, 4_500, at));
            if (i % 4 == 0) {
                wallets.execute("ravi", w -> w.creditTip(order, 2_000, at));
            }
        }
        wallets.execute("ravi", w -> w.creditEarning("o-3", 4_500, evening));   // duplicate delivery event: ignored
        wallets.execute("ravi", w -> w.adjust(-1_500, "Late delivery penalty o-7", evening.plusSeconds(3 * 3600)));
        RiderWallet before = wallets.execute("ravi", w -> w.requestPayout("po-1", 50_000, evening.plusSeconds(4 * 3600)));

        out.append(String.format(Locale.ROOT, "Events in stream: %d%n", store.readStream("wallet-ravi", 0).size()));
        out.append(String.format(Locale.ROOT, "Balance %s, available %s, payout pending %s%n",
                rupees(before.balancePaise()), rupees(before.availablePaise()), before.pendingPayoutId()));

        repository.load("ravi");
        out.append(String.format(Locale.ROOT, "Load with snapshots every 10 events reads %d events, not %d%n",
                repository.eventsReadOnLastLoad(), store.readStream("wallet-ravi", 0).size()));

        RiderWallet at9pm = repository.asOf("ravi", Instant.parse("2026-10-01T15:30:00Z"));
        out.append(String.format(Locale.ROOT, "As of 9:00 pm IST the balance was %s%n", rupees(at9pm.balancePaise())));

        // a 2023 event, stored before amounts became paise, upcast on read
        store.append("wallet-meena", 0, java.util.List.of(
                new dev.foodfleet.ch13.eventstore.NewEvent("WalletOpened", 1, java.util.Map.of("riderId", "meena", "occurredAt", "2023-08-01T10:00:00Z")),
                new dev.foodfleet.ch13.eventstore.NewEvent("EarningCredited", 1, java.util.Map.of("orderId", "o-old", "amountRupees", "38.50", "occurredAt", "2023-08-01T10:30:00Z"))));
        out.append(String.format(Locale.ROOT, "Meena's 2023 balance, from a v1 event in rupees: %s%n",
                rupees(repository.load("meena").balancePaise())));

        ProjectionRunner runner = new ProjectionRunner(store, 5);
        DailyEarningsProjection daily = new DailyEarningsProjection();
        PendingPayoutsProjection payouts = new PendingPayoutsProjection();
        int handled = runner.catchUp(daily);
        runner.catchUp(payouts);
        DailyEarningsProjection.DayTotals today = daily.day("ravi", LocalDate.of(2026, 10, 1));
        out.append(String.format(Locale.ROOT, "Projection daily-earnings handled %d events: %d deliveries, earnings %s, tips %s%n",
                handled, today.deliveries(), rupees(today.earningsPaise()), rupees(today.tipsPaise())));
        out.append(String.format(Locale.ROOT, "Projection pending-payouts: %d pending%n", payouts.count()));

        wallets.execute("ravi", w -> w.completePayout("po-1", evening.plusSeconds(5 * 3600)));
        out.append(String.format(Locale.ROOT, "After payout completes: read model still says %d pending (lag %d), ",
                payouts.count(), runner.lag(payouts)));
        runner.catchUp(payouts);
        out.append(String.format(Locale.ROOT, "after catch-up %d%n", payouts.count()));

        int replayed = runner.rebuild(daily);
        out.append(String.format(Locale.ROOT, "Rebuilt daily-earnings from scratch: %d events, same totals: %s%n",
                replayed, daily.day("ravi", LocalDate.of(2026, 10, 1)).equals(today)));
        return out.toString();
    }

    static String rupees(long paise) {
        return String.format(Locale.ROOT, "₹%d.%02d", paise / 100, Math.abs(paise % 100));
    }

}
