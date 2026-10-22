package dev.foodfleet.ch37.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeAudiencesTest {

    @Test
    void eachApiWhoseScopesWereGrantedIsAnAudience() {
        assertThat(ScopeAudiences.forScopes(Set.of("openid", "orders:read", "orders:write", "payments:capture")))
                .isEqualTo(List.of("order-service", "payment-service"));
        assertThat(ScopeAudiences.forScopes(Set.of("openid"))).isEqualTo(List.of());
    }
}
