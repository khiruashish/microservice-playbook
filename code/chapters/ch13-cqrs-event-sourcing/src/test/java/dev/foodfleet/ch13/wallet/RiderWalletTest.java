package dev.foodfleet.ch13.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Given events, when command, then events: the aggregate tested without any store. */
class RiderWalletTest {

    static final Instant T = Instant.parse("2026-10-01T14:00:00Z");

    private static RiderWallet given(WalletEvent... history) {
        RiderWallet w = RiderWallet.empty();
        for (WalletEvent e : history) {
            w.apply(e);
        }
        return w;
    }

    private final RiderWallet withSixHundred = given(
            new WalletEvent.WalletOpened("ravi", T),
            new WalletEvent.EarningCredited("o-1", 50_000, T),
            new WalletEvent.TipCredited("o-1", 10_000, T));

    @Test
    void payoutWithinTheAvailableBalance() {
        assertThat(withSixHundred.requestPayout("po-1", 40_000, T))
                .containsExactly(new WalletEvent.PayoutRequested("po-1", 40_000, T));
    }

    @Test
    void payoutRules() {
        assertThatThrownBy(() -> withSixHundred.requestPayout("po-1", 9_999, T)).hasMessage("Minimum payout is ₹100");
        assertThatThrownBy(() -> withSixHundred.requestPayout("po-1", 60_001, T))
                .hasMessage("Requested 60001 paise but only 60000 are available");

        RiderWallet inFlight = withSixHundred.apply(new WalletEvent.PayoutRequested("po-1", 40_000, T));
        assertThat(inFlight.availablePaise()).isEqualTo(20_000);
        assertThatThrownBy(() -> inFlight.requestPayout("po-2", 10_000, T)).hasMessage("Payout po-1 is still in progress");
    }

    @Test
    void aFailedPayoutMakesTheMoneyAvailableAgain() {
        RiderWallet w = withSixHundred
                .apply(new WalletEvent.PayoutRequested("po-1", 40_000, T))
                .apply(new WalletEvent.PayoutFailed("po-1", "Invalid IFSC", T));

        assertThat(w.availablePaise()).isEqualTo(60_000);
        assertThat(w.pendingPayoutId()).isNull();
    }

    @Test
    void aCompletedPayoutLeavesTheWallet() {
        RiderWallet w = withSixHundred
                .apply(new WalletEvent.PayoutRequested("po-1", 40_000, T))
                .apply(new WalletEvent.PayoutCompleted("po-1", T));

        assertThat(w.balancePaise()).isEqualTo(20_000);
        assertThat(w.lifetimeEarnedPaise()).isEqualTo(60_000);
    }

    @Test
    void theSameDeliveryIsNeverPaidTwice() {
        assertThat(withSixHundred.creditEarning("o-1", 50_000, T)).isEmpty();
        assertThat(withSixHundred.creditEarning("o-2", 45_000, T)).hasSize(1);
    }

    @Test
    void penaltiesCannotMakeTheBalanceNegative() {
        assertThatThrownBy(() -> withSixHundred.adjust(-60_001, "penalty", T)).isInstanceOf(WalletRuleViolation.class);
        assertThat(withSixHundred.adjust(-1_500, "late", T)).isEqualTo(List.of(new WalletEvent.AdjustmentApplied(-1_500, "late", T)));
    }
}
