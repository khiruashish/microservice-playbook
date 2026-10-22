package dev.foodfleet.ch51;

import dev.foodfleet.ch51.rules.CheckoutSlos;
import dev.foodfleet.ch51.rules.PrometheusSloRules;
import dev.foodfleet.ch51.rules.PrometheusSloRules.Spec;
import dev.foodfleet.ch51.slo.Slo;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusSloRulesTest {

    @Test
    void theCommittedRuleFileIsTheGeneratedOne() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/prometheus/checkout-slo.rules.yml")) {
            assertThat(in != null).isTrue();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(CheckoutSlos.rules());
        }
    }

    @Test
    void recordsEveryWindowTheAlertsUse() {
        String rules = CheckoutSlos.rules();
        for (String w : new String[] {"5m", "30m", "1h", "2h", "6h", "1d", "3d"}) {
            assertThat(rules).contains("record: slo:sli_error:ratio_rate" + w + "\n");
            assertThat(rules).contains("record: slo:sli_total:rate" + w + "\n");
        }
        assertThat(rules).contains("[30d]");
    }

    @Test
    void pageAndTicketConditionsUseTheWorkbookThresholds() {
        String rules = CheckoutSlos.rules();
        assertThat(rules).contains("slo:sli_error:ratio_rate1h{slo=\"checkout-availability\"} > 14.4 * 0.001 and slo:sli_error:ratio_rate5m{slo=\"checkout-availability\"} > 14.4 * 0.001");
        assertThat(rules).contains("or (slo:sli_error:ratio_rate6h{slo=\"checkout-availability\"} > 6 * 0.001");
        assertThat(rules).contains("slo:sli_error:ratio_rate3d{slo=\"checkout-latency\"} > 1 * 0.01");
        assertThat(rules).contains("severity: page");
        assertThat(rules).contains("severity: ticket");
    }

    @Test
    void theLowTrafficGuardIsOptional() {
        Slo slo = new Slo("refund-service", "refunds", 0.99, Duration.ofDays(30));
        String guarded = PrometheusSloRules.generate(new Spec(slo, "sum(rate(e[{{window}}]))", "sum(rate(t[{{window}}]))",
                Duration.ofMinutes(1), 100, "https://runbooks.example/refunds"));
        String plain = PrometheusSloRules.generate(new Spec(slo, "sum(rate(e[{{window}}]))", "sum(rate(t[{{window}}]))",
                Duration.ofMinutes(1), 0, "https://runbooks.example/refunds"));
        assertThat(guarded).contains("slo:sli_total:rate1h{slo=\"refunds\"} * 3600 >= 100");
        assertThat(plain.contains(">= 100")).isFalse();
        assertThat(plain).contains("interval: 1m");
        assertThat(plain).contains("alert: RefundsErrorBudgetBurn");
    }
}
