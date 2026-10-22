package dev.foodfleet.ch38.token;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch38.pki.DevCa;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CertificateBindingTest {

    private final DevCa ca = DevCa.create("foodfleet.internal", Duration.ofHours(1));
    private final DevCa.Credential orders = ca.issue("spiffe://foodfleet.internal/ns/orders/sa/order-service", Duration.ofMinutes(5));
    private final DevCa.Credential riders = ca.issue("spiffe://foodfleet.internal/ns/riders/sa/rider-service", Duration.ofMinutes(5));

    @Test
    void aBoundTokenWorksOnlyWithItsCertificate() {
        Map<String, Object> claims = Map.of("sub", "order-service",
                "cnf", CertificateBinding.confirmation(orders.leaf()));
        assertThat(CertificateBinding.check(claims, orders.leaf()).accepted()).isTrue();
        assertThat(CertificateBinding.check(claims, riders.leaf()).accepted()).isFalse();
        assertThat(CertificateBinding.check(claims, null).reason()).contains("no client certificate");
    }

    @Test
    void anUnboundTokenIsABearerTokenForWhoeverHoldsIt() {
        Map<String, Object> claims = Map.of("sub", "order-service");
        assertThat(CertificateBinding.check(claims, orders.leaf()).accepted()).isTrue();
        assertThat(CertificateBinding.check(claims, riders.leaf()).accepted()).isTrue();
        assertThat(CertificateBinding.check(claims, null).accepted()).isTrue();
    }

    @Test
    void theThumbprintIsBase64UrlOfSha256OverTheDerCertificate() {
        String t = CertificateBinding.thumbprint(orders.leaf());
        assertThat(t.length()).isEqualTo(43);     // 32 bytes, unpadded base64url
        assertThat(t.contains("=") || t.contains("+") || t.contains("/")).isFalse();
    }
}
