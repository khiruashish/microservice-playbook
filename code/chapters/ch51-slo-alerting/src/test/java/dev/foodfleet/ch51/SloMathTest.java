package dev.foodfleet.ch51;

import dev.foodfleet.ch51.slo.BurnRateAlert;
import dev.foodfleet.ch51.slo.BurnRateAlert.Severity;
import dev.foodfleet.ch51.slo.ErrorBudget;
import dev.foodfleet.ch51.slo.Slo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SloMathTest {

    static final Slo CHECKOUT = new Slo("order-service", "checkout-availability", 0.999, Duration.ofDays(30));

    @Test
    void errorBudgetOf999OverThirtyDays() {
        assertThat(Math.abs(CHECKOUT.errorBudget() - 0.001) < 1e-12).isTrue();
        assertThat(CHECKOUT.budgetAsOutage()).isEqualTo(Duration.ofSeconds(2592));          // 43.2 minutes
        assertThat(CHECKOUT.allowedBadEvents(43_200_000)).isEqualTo(43_200L);
        assertThat(Math.abs(CHECKOUT.burnRate(0.0144) - 14.4) < 1e-9).isTrue();
    }

    @Test
    void rejectsImpossibleObjectives() {
        for (double bad : new double[] {0, 1, 1.5, -0.1}) {
            boolean thrown = false;
            try {
                new Slo("s", "n", bad, Duration.ofDays(30));
            } catch (IllegalArgumentException e) {
                thrown = true;
            }
            assertThat(thrown).isTrue();
        }
    }

    @Test
    void workbookBurnRatesForThirtyDays() {
        List<BurnRateAlert> alerts = BurnRateAlert.defaults(CHECKOUT);
        double[] rates = {14.4, 6, 3, 1};
        Duration[] shorts = {Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6)};
        double[] spent = {0.02, 0.05, 0.10, 0.10};
        for (int i = 0; i < 4; i++) {
            BurnRateAlert a = alerts.get(i);
            assertThat(Math.abs(a.burnRate() - rates[i]) < 1e-9).isTrue();
            assertThat(a.shortWindow()).isEqualTo(shorts[i]);
            assertThat(Math.abs(a.budgetSpentAtThreshold(CHECKOUT) - spent[i]) < 1e-9).isTrue();
        }
        assertThat(alerts.get(0).severity() == Severity.PAGE && alerts.get(1).severity() == Severity.PAGE).isTrue();
        assertThat(alerts.get(2).severity() == Severity.TICKET && alerts.get(3).severity() == Severity.TICKET).isTrue();
        assertThat(Math.abs(alerts.get(0).errorRatioThreshold(CHECKOUT) - 0.0144) < 1e-12).isTrue();
    }

    @Test
    void burnRatesScaleWithTheWindow() {
        Slo weekly = new Slo("s", "n", 0.999, Duration.ofDays(7));
        // 2% of a 7-day budget in an hour: 0.02 * 168 = 3.36
        assertThat(Math.abs(BurnRateAlert.defaults(weekly).get(0).burnRate() - 3.36) < 1e-9).isTrue();
    }

    @Test
    void budgetSpentRemainingAndPolicy() {
        ErrorBudget fresh = new ErrorBudget(CHECKOUT, 1_000_000, 200);
        assertThat(Math.abs(fresh.spent() - 0.2) < 1e-9).isTrue();
        assertThat(fresh.policy()).isEqualTo(ErrorBudget.Policy.NORMAL);
        assertThat(new ErrorBudget(CHECKOUT, 1_000_000, 500).policy()).isEqualTo(ErrorBudget.Policy.CAUTION);
        assertThat(new ErrorBudget(CHECKOUT, 1_000_000, 1_000).policy()).isEqualTo(ErrorBudget.Policy.FREEZE);
        assertThat(Math.abs(new ErrorBudget(CHECKOUT, 1_000_000, 1_500).remaining() + 0.5) < 1e-9).isTrue();
        assertThat(new ErrorBudget(CHECKOUT, 0, 0).spent() == 0).isTrue();
    }

    @Test
    void timeToExhaustionAtTheRecentBurnRate() {
        ErrorBudget half = new ErrorBudget(CHECKOUT, 1_000_000, 500);
        // half the budget left, burning at 10x: 0.5 * 30 days / 10 = 1.5 days
        assertThat(half.timeToExhaustion(10).orElseThrow()).isEqualTo(Duration.ofHours(36));
        assertThat(half.timeToExhaustion(0).isPresent()).isFalse();
        assertThat(new ErrorBudget(CHECKOUT, 1_000_000, 2_000).timeToExhaustion(1).isPresent()).isFalse();
    }
}
