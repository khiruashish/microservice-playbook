package dev.foodfleet.ch38.spiffe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch38.pki.DevCa;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpiffeTrustManagerTest {

    private static final String ORDERS = "spiffe://foodfleet.internal/ns/orders/sa/order-service";
    private static final String PAYMENTS = "spiffe://foodfleet.internal/ns/payments/sa/payment-service";

    private final DevCa foodfleet = DevCa.create("foodfleet.internal", Duration.ofHours(1));
    private final List<String> rejections = new ArrayList<>();
    private final SpiffeTrustManager server = SpiffeTrustManager.anyMember(
            TrustBundles.of("foodfleet.internal", foodfleet.certificate()), rejections::add);

    @Test
    void anSvidFromTheTrustedDomainIsAccepted() throws Exception {
        assertThat(server.verify(foodfleet.issue(ORDERS, Duration.ofMinutes(5)).chain()))
                .isEqualTo(SpiffeId.parse(ORDERS));
        assertThat(rejections).isEmpty();
    }

    @Test
    void anotherTrustDomainNeedsItsOwnBundle() throws Exception {
        DevCa partner = DevCa.create("partner.example", Duration.ofHours(1));
        var svid = partner.issue("spiffe://partner.example/ns/couriers/sa/courier-gateway", Duration.ofMinutes(5));
        assertThatThrownBy(() -> server.verify(svid.chain())).hasMessageContaining("no trust bundle for trust domain");

        server.updateBundles(TrustBundles.of("foodfleet.internal", foodfleet.certificate())
                .with("partner.example", partner.certificate()));
        assertThat(server.verify(svid.chain())).isEqualTo(SpiffeId.parse("spiffe://partner.example/ns/couriers/sa/courier-gateway"));
        assertThat(rejections).hasSize(1);
    }

    @Test
    void aForeignCaCannotVouchForOurDomain() {
        DevCa rogue = DevCa.create("foodfleet.internal", Duration.ofHours(1));     // same name, different key
        var forged = rogue.issue(ORDERS, Duration.ofMinutes(5));
        assertThatThrownBy(() -> server.verify(forged.chain())).hasMessageContaining("path validation failed");

        // federating a partner does not let the partner's CA sign foodfleet.internal identities either
        DevCa partner = DevCa.create("partner.example", Duration.ofHours(1));
        server.updateBundles(TrustBundles.of("foodfleet.internal", foodfleet.certificate())
                .with("partner.example", partner.certificate()));
        assertThatThrownBy(() -> server.verify(partner.issue(ORDERS, Duration.ofMinutes(5)).chain()))
                .hasMessageContaining("path validation failed");
    }

    @Test
    void anExpiredSvidIsRejected() {
        Instant now = Instant.now();
        var expired = foodfleet.issue(new DevCa.Spec().uris(ORDERS).validity(now.minusSeconds(3600), now.minusSeconds(60)));
        assertThatThrownBy(() -> server.verify(expired.chain())).hasMessageContaining("path validation failed");
    }

    @Test
    void aClientAcceptsOnlyTheServerItMeantToCall() throws Exception {
        var bundles = TrustBundles.of("foodfleet.internal", foodfleet.certificate());
        var client = SpiffeTrustManager.expecting(bundles, SpiffeId.parse(PAYMENTS), rejections::add);
        assertThat(client.verify(foodfleet.issue(PAYMENTS, Duration.ofMinutes(5)).chain()))
                .isEqualTo(SpiffeId.parse(PAYMENTS));
        // a valid SVID of the wrong workload: e.g. traffic redirected to another pod in the mesh
        assertThatThrownBy(() -> client.verify(foodfleet.issue(ORDERS, Duration.ofMinutes(5)).chain()))
                .hasMessageContaining("is not authorized");
    }

    @Test
    void caRotationNeedsAnOverlapWhereBothCasAreTrusted() throws Exception {
        DevCa next = DevCa.create("foodfleet.internal", Duration.ofHours(2));
        var oldSvid = foodfleet.issue(ORDERS, Duration.ofMinutes(5));
        var newSvid = next.issue(ORDERS, Duration.ofMinutes(5));

        server.updateBundles(TrustBundles.of("foodfleet.internal", foodfleet.certificate(), next.certificate()));
        server.verify(oldSvid.chain());
        server.verify(newSvid.chain());

        server.updateBundles(TrustBundles.of("foodfleet.internal", next.certificate()));
        assertThatThrownBy(() -> server.verify(oldSvid.chain())).hasMessageContaining("path validation failed");
    }
}
