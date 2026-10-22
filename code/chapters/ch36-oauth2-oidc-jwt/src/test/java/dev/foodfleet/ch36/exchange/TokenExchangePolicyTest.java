package dev.foodfleet.ch36.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch36.jwt.VerifiedJwt;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenExchangePolicyTest {

    static final Instant NOW = Instant.parse("2025-04-14T10:00:00Z");
    final TokenExchangePolicy policy = new TokenExchangePolicy(List.of(
            new TokenExchangePolicy.ClientRules("order-service",
                    Map.of("payment-service", Set.of("payments:capture", "payments:refund")))),
            Duration.ofMinutes(5));

    static VerifiedJwt userToken(Set<String> scopes, String aud) {
        return new VerifiedJwt("c01", "customer-app", scopes, NOW.plus(Duration.ofMinutes(15)), "k1",
                Map.of("aud", aud, "sub", "c01"));
    }

    @Test
    void theNewTokenIsForOneAudienceWithFewerScopesForLessTime() {
        var grant = policy.exchange("order-service",
                userToken(Set.of("orders:read", "orders:write", "payments:capture"), "order-service"),
                "payment-service", Set.of("payments:capture"), "https://id", NOW);
        assertThat(grant.claims().get("aud")).isEqualTo("payment-service");
        assertThat(grant.claims().get("scope")).isEqualTo("payments:capture");
        assertThat(grant.claims().get("sub")).isEqualTo("c01");
        assertThat(grant.claims().get("act")).isEqualTo(Map.of("sub", "order-service"));
        assertThat(grant.claims().get("exp")).isEqualTo(NOW.plus(Duration.ofMinutes(5)).getEpochSecond());
    }

    @Test
    void scopesTheUserNeverHadCannotBeGained() {
        assertThatThrownBy(() -> policy.exchange("order-service", userToken(Set.of("orders:read"), "order-service"),
                "payment-service", Set.of("payments:refund"), "https://id", NOW))
                .isInstanceOf(TokenExchangePolicy.Refused.class).hasMessageContaining("none of the requested scopes");
    }

    @Test
    void onlyAllowedAudiencesAndOnlyTokensIssuedToTheCaller() {
        var token = userToken(Set.of("payments:capture"), "order-service");
        assertThatThrownBy(() -> policy.exchange("order-service", token, "loyalty-service", Set.of(), "https://id", NOW))
                .hasMessageContaining("may not obtain tokens for loyalty-service");
        assertThatThrownBy(() -> policy.exchange("order-service", userToken(Set.of("payments:capture"), "menu-service"),
                "payment-service", Set.of(), "https://id", NOW)).hasMessageContaining("not issued to this client");
        assertThatThrownBy(() -> policy.exchange("menu-service", token, "payment-service", Set.of(), "https://id", NOW))
                .hasMessageContaining("may not exchange");
    }
}
