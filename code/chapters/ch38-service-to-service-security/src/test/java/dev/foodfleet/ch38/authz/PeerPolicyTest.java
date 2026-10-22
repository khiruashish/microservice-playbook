package dev.foodfleet.ch38.authz;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch38.spiffe.SpiffeId;
import org.junit.jupiter.api.Test;

class PeerPolicyTest {

    private static final SpiffeId ORDERS = SpiffeId.ofWorkload("foodfleet.internal", "orders", "order-service");
    private static final SpiffeId RIDERS = SpiffeId.ofWorkload("foodfleet.internal", "riders", "rider-service");
    private static final SpiffeId SETTLEMENT = SpiffeId.ofWorkload("foodfleet.internal", "payments", "settlement-job");

    private final PeerPolicy policy = PeerPolicy.builder()
            .allow("capture", "POST", "/payments/*/capture", ORDERS)
            .allow("refund", "POST", "/payments/*/refund", ORDERS)
            .allow("settle", "POST", "/internal/settlements/run", SETTLEMENT)
            .build();

    @Test
    void namedCallersReachTheirRoutes() {
        assertThat(policy.decide(ORDERS, "POST", "/payments/42/capture").allowed()).isTrue();
        assertThat(policy.decide(ORDERS, "post", "/payments/42/refund").allowed()).isTrue();
        assertThat(policy.decide(SETTLEMENT, "POST", "/internal/settlements/run").allowed()).isTrue();
    }

    @Test
    void aValidWorkloadIdentityIsNotPermissionForEverything() {
        assertThat(policy.decide(RIDERS, "POST", "/payments/42/capture").allowed()).isFalse();
        assertThat(policy.decide(ORDERS, "POST", "/internal/settlements/run").allowed()).isFalse();
        assertThat(policy.decide(ORDERS, "GET", "/payments/42/capture").allowed()).isFalse();
    }

    @Test
    void routesWithoutARuleAreClosed() {
        var d = policy.decide(ORDERS, "DELETE", "/payments/42");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).startsWith("no rule for");
        assertThat(policy.decide(ORDERS, "POST", "/payments/42/capture/extra").allowed()).isFalse();
    }

    @Test
    void pathsAnotherLayerMightReadDifferentlyAreDenied() {
        String[] ambiguous = {
            "/payments/42/%63apture",                 // percent-encoded 'c'
            "/payments/42/capture;jsessionid=x",      // path parameter
            "/internal/settlements/../../payments/42/capture",
            "/payments//42/capture",
            "/payments/./42/capture",
            "/payments\\42\\capture",
        };
        for (String path : ambiguous) {
            assertThat(policy.decide(ORDERS, "POST", path).allowed()).isFalse();
            assertThat(policy.decide(ORDERS, "POST", path).reason()).startsWith("ambiguous path");
        }
    }
}
