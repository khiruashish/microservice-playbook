package dev.foodfleet.ch38.spiffe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpiffeIdTest {

    @Test
    void workloadIdsParseIntoTrustDomainAndPath() {
        SpiffeId id = SpiffeId.parse("spiffe://foodfleet.internal/ns/orders/sa/order-service");
        assertThat(id.trustDomain()).isEqualTo("foodfleet.internal");
        assertThat(id.path()).isEqualTo("/ns/orders/sa/order-service");
        assertThat(id).isEqualTo(SpiffeId.ofWorkload("foodfleet.internal", "orders", "order-service"));
        assertThat(id.toString()).isEqualTo("spiffe://foodfleet.internal/ns/orders/sa/order-service");
        assertThat(SpiffeId.parse("spiffe://foodfleet.internal").path()).isEqualTo("");
    }

    @Test
    void everythingTheStandardForbidsIsRejected() {
        String[] invalid = {
            "https://foodfleet.internal/ns/orders/sa/order-service",   // scheme
            "spiffe://FoodFleet.internal/ns/orders",                     // uppercase trust domain
            "spiffe://foodfleet.internal:8443/ns/orders",               // port
            "spiffe://admin@foodfleet.internal/ns/orders",              // userinfo
            "spiffe://foodfleet.internal/ns/orders?x=1",                // query
            "spiffe://foodfleet.internal/ns/orders#frag",               // fragment
            "spiffe://foodfleet.internal/ns/%6Frders",                  // percent-encoding
            "spiffe://foodfleet.internal/ns/orders/",                   // trailing slash
            "spiffe://foodfleet.internal/ns//orders",                   // empty segment
            "spiffe://foodfleet.internal/ns/../payments",               // dot segment
            "spiffe:///ns/orders",                                      // empty trust domain
            "spiffe://foodfleet.internal/" + "a".repeat(2048),          // longer than 2048 bytes
        };
        for (String s : invalid) {
            assertThatThrownBy(() -> SpiffeId.parse(s)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void equalityIsExactSoThereIsOneSpellingPerIdentity() {
        assertThat(SpiffeId.parse("spiffe://foodfleet.internal/ns/orders/sa/Order-Service"))
                .isNotEqualTo(SpiffeId.parse("spiffe://foodfleet.internal/ns/orders/sa/order-service"));
    }
}
