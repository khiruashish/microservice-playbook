package dev.foodfleet.ch49;

import dev.foodfleet.ch49.baggage.BaggagePolicy;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaggagePolicyTest {

    @Test
    void onlyAllowListedKeysSurviveTheEdge() {
        Baggage incoming = Baggage.builder().put("tenant", "chain-7").put("city.tier", "metro")
                .put("user.email", "priya@example.com").put("session", "eyJhbGciOiJIUzI1NiJ9.demo").build();

        Baggage clean = BaggagePolicy.sanitize(incoming);

        assertThat(clean.asMap().keySet()).containsExactly("city.tier", "tenant");
        assertThat(clean.getEntryValue("user.email")).isNull();
        assertThat(clean.getEntryValue("session")).isNull();
    }

    @Test
    void entriesStopOnceTheHeaderWouldBeTooBig() {
        Baggage incoming = Baggage.builder().put("tenant", "chain-7").put("experiment", "x".repeat(600)).build();

        Baggage clean = BaggagePolicy.sanitize(incoming);

        assertThat(clean.getEntryValue("tenant")).isEqualTo("chain-7");
        assertThat(clean.getEntryValue("experiment")).isNull();
    }

    @Test
    void thirdPartyCallsRunWithNoBaggageAndItComesBackAfterwards() {
        try (Scope ignored = Baggage.builder().put("tenant", "chain-7").build().makeCurrent()) {
            int inside = BaggagePolicy.withoutBaggage(() -> Baggage.current().size());
            assertThat((long) inside).isEqualTo(0);
            assertThat(Baggage.current().getEntryValue("tenant")).isEqualTo("chain-7");
        }
    }
}
