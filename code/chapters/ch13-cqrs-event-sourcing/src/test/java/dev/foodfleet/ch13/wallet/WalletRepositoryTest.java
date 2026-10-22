package dev.foodfleet.ch13.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch13.eventstore.EventStore.ConcurrencyException;
import dev.foodfleet.ch13.eventstore.InMemoryEventStore;
import dev.foodfleet.ch13.eventstore.NewEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WalletRepositoryTest {

    static final Instant T = Instant.parse("2026-10-01T14:00:00Z");

    final InMemoryEventStore store = new InMemoryEventStore(Clock.systemUTC());
    final WalletRepository repository = new WalletRepository(store, 10);
    final WalletService service = new WalletService(repository);

    void sixHundred() {
        service.execute("ravi", w -> w.open("ravi", T));
        service.execute("ravi", w -> w.creditEarning("o-1", 60_000, T));
    }

    @Test
    void twoPayoutsRacingForTheSameMoney() {
        sixHundred();
        RiderWallet phone = repository.load("ravi");
        RiderWallet web = repository.load("ravi");

        repository.save("ravi", phone, phone.requestPayout("po-phone", 40_000, T));

        assertThatThrownBy(() -> repository.save("ravi", web, web.requestPayout("po-web", 40_000, T)))
                .isInstanceOf(ConcurrencyException.class);
    }

    @Test
    void theServiceRetriesAndReChecksTheRules() {
        sixHundred();
        service.execute("ravi", w -> w.requestPayout("po-1", 40_000, T));

        assertThatThrownBy(() -> service.execute("ravi", w -> w.requestPayout("po-2", 10_000, T)))
                .isInstanceOf(WalletRuleViolation.class)
                .hasMessage("Payout po-1 is still in progress");
    }

    @Test
    void snapshotsChangeLoadCostNotState() {
        sixHundred();
        for (int i = 2; i <= 25; i++) {
            String order = "o-" + i;
            service.execute("ravi", w -> w.creditEarning(order, 1_000, T));
        }
        RiderWallet withSnapshots = repository.load("ravi");
        long readWithSnapshots = repository.eventsReadOnLastLoad();
        RiderWallet withoutSnapshots = new WalletRepository(store, Integer.MAX_VALUE).load("ravi");

        assertThat(withSnapshots.snapshot()).isEqualTo(withoutSnapshots.snapshot());
        assertThat(withSnapshots.version()).isEqualTo(26);
        assertThat(readWithSnapshots).isEqualTo(6);
    }

    @Test
    void temporalQueryReplaysOnlyEarlierEvents() {
        service.execute("ravi", w -> w.open("ravi", T));
        service.execute("ravi", w -> w.creditEarning("o-1", 45_000, T.plusSeconds(60)));
        service.execute("ravi", w -> w.creditEarning("o-2", 45_000, T.plusSeconds(7_200)));

        assertThat(repository.asOf("ravi", T.plusSeconds(3_600)).balancePaise()).isEqualTo(45_000);
        assertThat(repository.load("ravi").balancePaise()).isEqualTo(90_000);
    }

    @Test
    void version1EventsAreUpcastOnRead() {
        store.append("wallet-meena", 0, List.of(
                new NewEvent("WalletOpened", 1, Map.of("riderId", "meena", "occurredAt", "2023-08-01T10:00:00Z")),
                new NewEvent("EarningCredited", 1, Map.of("orderId", "o-old", "amountRupees", "38.50", "occurredAt", "2023-08-01T10:30:00Z"))));

        assertThat(repository.load("meena").balancePaise()).isEqualTo(3_850);
        assertThat(store.readStream("wallet-meena", 0).get(1).payload()).containsKey("amountRupees");   // history untouched
    }
}
