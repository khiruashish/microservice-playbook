package dev.foodfleet.ch38.spiffe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch38.pki.DevCa;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class X509SvidTest {

    private static final String ORDERS = "spiffe://foodfleet.internal/ns/orders/sa/order-service";
    private final DevCa ca = DevCa.create("foodfleet.internal", Duration.ofHours(1));

    @Test
    void aWellFormedSvidYieldsItsSpiffeId() throws Exception {
        var svid = ca.issue(ORDERS, Duration.ofMinutes(5));
        assertThat(X509Svid.spiffeId(svid.leaf())).isEqualTo(SpiffeId.parse(ORDERS));
    }

    @Test
    void anOrdinaryTlsCertificateIsNotAnSvid() {
        var cert = ca.issue(new DevCa.Spec().dnsNames("order-service.orders.svc"));
        assertThatThrownBy(() -> X509Svid.spiffeId(cert.leaf())).hasMessageContaining("no URI SAN");
    }

    @Test
    void twoIdentitiesInOneCertificateAreRejected() {
        var cert = ca.issue(new DevCa.Spec().uris(ORDERS, "spiffe://foodfleet.internal/ns/payments/sa/payment-service"));
        assertThatThrownBy(() -> X509Svid.spiffeId(cert.leaf())).hasMessageContaining("more than one URI SAN");
    }

    @Test
    void aSigningCertificateCannotAuthenticate() {
        var cert = ca.issue(new DevCa.Spec().uris(ORDERS).ca(true));
        assertThatThrownBy(() -> X509Svid.spiffeId(cert.leaf())).hasMessageContaining("cA=true");
        assertThatThrownBy(() -> X509Svid.spiffeId(ca.certificate())).hasMessageContaining("has no path");
    }

    @Test
    void perRequestChecksCatchACertificateThatExpiredAfterTheHandshake() throws Exception {
        var svid = ca.issue(ORDERS, Duration.ofMinutes(5));
        var now = java.time.Instant.now();
        assertThat(X509Svid.spiffeIdValidAt(svid.leaf(), now)).isEqualTo(SpiffeId.parse(ORDERS));
        assertThatThrownBy(() -> X509Svid.spiffeIdValidAt(svid.leaf(), now.plus(Duration.ofMinutes(6))))
                .hasMessageContaining("expired at");
    }

    @Test
    void anIdThatIsNotAValidSpiffeIdIsRejected() {
        var cert = ca.issue(new DevCa.Spec().uris("spiffe://FoodFleet.internal/ns/orders/sa/order-service"));
        assertThatThrownBy(() -> X509Svid.spiffeId(cert.leaf())).hasMessageContaining("not a valid SPIFFE ID");
    }
}
