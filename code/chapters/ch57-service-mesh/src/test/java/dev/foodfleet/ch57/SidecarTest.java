package dev.foodfleet.ch57;

import dev.foodfleet.ch57.mesh.Sidecar;
import dev.foodfleet.ch57.mesh.Sidecar.Outlier;
import dev.foodfleet.ch57.mesh.Sidecar.Retry;
import dev.foodfleet.ch57.mesh.Sidecar.Tls;
import dev.foodfleet.ch57.mesh.Sidecar.Upstream;
import dev.foodfleet.ch57.mesh.SpiffeId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SidecarTest {

    static final SpiffeId GATEWAY = SpiffeId.parse("spiffe://foodfleet.local/ns/mesh/sa/gateway");
    static final SpiffeId MENU = new SpiffeId("foodfleet.local", "mesh", "menu-service");
    static final Tls TLS = new Tls("/c/tls.crt", "/c/tls.key", "/c/ca.crt", List.of(MENU));

    @Test
    void parsesAndPrintsWorkloadIdentities() {
        assertThat(GATEWAY.serviceAccount()).isEqualTo("gateway");
        assertThat(MENU.toString()).isEqualTo("spiffe://foodfleet.local/ns/mesh/sa/menu-service");
        boolean rejected = false;
        try {
            SpiffeId.parse("spiffe://foodfleet.local/menu-service");
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        assertThat(rejected).isTrue();
    }

    @Test
    void outboundBalancesEveryPodOfAHeadlessService() {
        String y = Sidecar.outbound(15001, new Upstream("menu-service", "menu-headless.mesh.svc.cluster.local", 15006, null, null, null));
        assertThat(y).contains("type: STRICT_DNS");
        assertThat(y).contains("lb_policy: ROUND_ROBIN");
        assertThat(y).contains("address: menu-headless.mesh.svc.cluster.local, port_value: 15006");
        assertThat(y.contains("retry_policy") || y.contains("outlier_detection") || y.contains("transport_socket")).isFalse();
    }

    @Test
    void retriesGoToAnotherPodAndOutliersAreEjected() {
        String y = Sidecar.outbound(15001, new Upstream("menu-service", "menu-headless", 15006, new Retry(2, Duration.ofMillis(500)),
                new Outlier(5, Duration.ofSeconds(2), Duration.ofSeconds(30), 50), null));
        assertThat(y).contains("num_retries: 2");
        assertThat(y).contains("per_try_timeout: 0.500s");
        assertThat(y).contains("envoy.retry_host_predicates.previous_hosts");
        assertThat(y).contains("consecutive_5xx: 5");
        assertThat(y).contains("max_ejection_percent: 50");
        // the retry policy belongs to the route: indented under "route:" (Envoy rejects it anywhere else)
        assertThat(y).contains("\n" + " ".repeat(28) + "retry_policy:");
    }

    @Test
    void mtlsChecksThePeersIdentityBothWays() {
        String out = Sidecar.outbound(15001, new Upstream("menu-service", "menu-headless", 15006, null, null, TLS));
        assertThat(out).contains("UpstreamTlsContext");
        assertThat(out).contains("{san_type: URI, matcher: {exact: \"spiffe://foodfleet.local/ns/mesh/sa/menu-service\"}}");
        String in = Sidecar.inbound(15006, 8080, new Tls("/c/tls.crt", "/c/tls.key", "/c/ca.crt", List.of(GATEWAY)));
        assertThat(in).contains("DownstreamTlsContext");
        assertThat(in).contains("require_client_certificate: true");
        assertThat(in).contains("spiffe://foodfleet.local/ns/mesh/sa/gateway");
        assertThat(in).contains("address: 127.0.0.1, port_value: 8080");
    }
}
