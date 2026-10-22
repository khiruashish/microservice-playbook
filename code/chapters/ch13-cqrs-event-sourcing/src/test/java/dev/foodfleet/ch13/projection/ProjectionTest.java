package dev.foodfleet.ch13.projection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch13.eventstore.InMemoryEventStore;
import dev.foodfleet.ch13.wallet.WalletRepository;
import dev.foodfleet.ch13.wallet.WalletService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProjectionTest {

    static final Instant EVENING = Instant.parse("2026-10-01T13:30:00Z");     // 7 pm IST
    static final Instant LATE = Instant.parse("2026-10-01T18:45:00Z");        // 00:15 IST, next day

    final InMemoryEventStore store = new InMemoryEventStore(Clock.systemUTC());
    final WalletService wallets = new WalletService(new WalletRepository(store, 50));
    final ProjectionRunner runner = new ProjectionRunner(store, 3);

    void evening() {
        for (String rider : new String[] {"ravi", "asha"}) {
            wallets.execute(rider, w -> w.open(rider, EVENING));
        }
        wallets.execute("ravi", w -> w.creditEarning("o-1", 45_000, EVENING));
        wallets.execute("ravi", w -> w.creditTip("o-1", 2_000, EVENING));
        wallets.execute("asha", w -> w.creditEarning("o-2", 52_000, EVENING));
        wallets.execute("ravi", w -> w.creditEarning("o-3", 45_000, LATE));
    }

    @Test
    void dailyTotalsUseIndianCalendarDays() {
        evening();
        DailyEarningsProjection daily = new DailyEarningsProjection();

        assertThat(runner.catchUp(daily)).isEqualTo(6);
        assertThat(daily.day("ravi", LocalDate.of(2026, 10, 1)))
                .isEqualTo(new DailyEarningsProjection.DayTotals(45_000, 2_000, 1));
        assertThat(daily.day("ravi", LocalDate.of(2026, 10, 2)).deliveries()).isEqualTo(1);
        assertThat(daily.leaderboard(LocalDate.of(2026, 10, 1), 2)).extracting(DailyEarningsProjection.Leader::riderId)
                .containsExactly("asha", "ravi");
    }

    @Test
    void checkpointsMeanEachEventIsHandledOnce() {
        evening();
        DailyEarningsProjection daily = new DailyEarningsProjection();
        runner.catchUp(daily);

        assertThat(runner.catchUp(daily)).isZero();
        assertThat(runner.checkpoint(daily)).isEqualTo(6);
    }

    @Test
    void readModelsLagUntilTheyCatchUp() {
        evening();
        wallets.execute("asha", w -> w.requestPayout("po-1", 50_000, EVENING.plusSeconds(600)));
        PendingPayoutsProjection payouts = new PendingPayoutsProjection();
        runner.catchUp(payouts);
        wallets.execute("asha", w -> w.completePayout("po-1", EVENING.plusSeconds(1_200)));

        assertThat(payouts.count()).isEqualTo(1);
        assertThat(runner.lag(payouts)).isEqualTo(1);
        runner.catchUp(payouts);
        assertThat(payouts.count()).isZero();
    }

    @Test
    void stuckPayoutsShowUpOnTheOpsDashboard() {
        evening();
        wallets.execute("asha", w -> w.requestPayout("po-1", 50_000, EVENING));
        PendingPayoutsProjection payouts = new PendingPayoutsProjection();
        runner.catchUp(payouts);

        assertThat(payouts.olderThan(Duration.ofHours(2), EVENING.plusSeconds(3 * 3600)))
                .extracting(PendingPayoutsProjection.Pending::riderId).containsExactly("asha");
    }

    @Test
    void rebuildingFromScratchGivesTheSameReadModel() {
        evening();
        DailyEarningsProjection daily = new DailyEarningsProjection();
        runner.catchUp(daily);
        var before = daily.leaderboard(LocalDate.of(2026, 10, 1), 10);

        assertThat(runner.rebuild(daily)).isEqualTo(6);
        assertThat(daily.leaderboard(LocalDate.of(2026, 10, 1), 10)).isEqualTo(before);
    }
}
