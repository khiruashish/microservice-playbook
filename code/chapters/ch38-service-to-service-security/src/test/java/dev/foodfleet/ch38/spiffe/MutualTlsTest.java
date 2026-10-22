package dev.foodfleet.ch38.spiffe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch38.pki.DevCa;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real TLS 1.3 on loopback: the server answers each line with the caller's SPIFFE ID and certificate serial. */
class MutualTlsTest {

    private static final SpiffeId ORDERS = SpiffeId.parse("spiffe://foodfleet.internal/ns/orders/sa/order-service");
    private static final SpiffeId PAYMENTS = SpiffeId.parse("spiffe://foodfleet.internal/ns/payments/sa/payment-service");

    private final DevCa ca = DevCa.create("foodfleet.internal", Duration.ofHours(1));
    private final TrustBundles bundles = TrustBundles.of("foodfleet.internal", ca.certificate());
    private final List<String> rejections = Collections.synchronizedList(new ArrayList<>());
    private SSLServerSocket server;

    @BeforeEach
    void start() throws IOException {
        var svid = ca.issue(PAYMENTS.toString(), Duration.ofMinutes(5));
        SSLContext ctx = SpiffeTls.context(new RotatingKeyManager(svid.privateKey(), svid.chain()),
                SpiffeTrustManager.anyMember(bundles, rejections::add));
        server = SpiffeTls.mutualTlsServer(ctx, 0);
        Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try {
                    SSLSocket s = (SSLSocket) server.accept();
                    Thread.ofVirtual().start(() -> serve(s));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
    }

    private static void serve(SSLSocket s) {
        try (s) {
            var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = s.getOutputStream();
            while (in.readLine() != null) {
                var peer = (X509Certificate) s.getSession().getPeerCertificates()[0];
                out.write((X509Svid.spiffeId(peer) + " " + peer.getSerialNumber() + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception ignored) {
            // handshake failures end here; the trust manager recorded why
        }
    }

    private static String call(SSLSocket s) throws IOException {
        s.getOutputStream().write("hello\n".getBytes(StandardCharsets.UTF_8));
        s.getOutputStream().flush();
        return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
    }

    private SSLContext client(RotatingKeyManager keys) {
        return SpiffeTls.context(keys, SpiffeTrustManager.expecting(bundles, PAYMENTS, rejections::add));
    }

    @Test
    void bothSidesLearnEachOthersSpiffeId() throws Exception {
        var svid = ca.issue(ORDERS.toString(), Duration.ofMinutes(5));
        try (SSLSocket s = SpiffeTls.connect(client(new RotatingKeyManager(svid.privateKey(), svid.chain())), server.getLocalPort())) {
            assertThat(call(s)).startsWith(ORDERS.toString());
            assertThat(X509Svid.spiffeId((X509Certificate) s.getSession().getPeerCertificates()[0])).isEqualTo(PAYMENTS);
            assertThat(s.getSession().getProtocol()).isEqualTo("TLSv1.3");
        }
    }

    @Test
    void aClientWithoutACertificateGetsNoConnection() throws Exception {
        SSLContext noCert = SSLContext.getInstance("TLSv1.3");
        noCert.init(null, new TrustManager[] {SpiffeTrustManager.expecting(bundles, PAYMENTS, r -> { })}, null);
        // TLS 1.3: the client finishes its side first, then learns of the rejection from the server's alert
        assertThatThrownBy(() -> {
            try (SSLSocket s = SpiffeTls.connect(noCert, server.getLocalPort())) {
                call(s);
            }
        }).isInstanceOf(IOException.class);
    }

    @Test
    void aRotatedSvidIsUsedOnTheNextFullHandshakeWhileOldConnectionsCarryOn() throws Exception {
        var first = ca.issue(ORDERS.toString(), Duration.ofMinutes(5));
        var keys = new RotatingKeyManager(first.privateKey(), first.chain());
        SSLContext ctx = client(keys);
        try (SSLSocket before = SpiffeTls.connect(ctx, server.getLocalPort())) {
            assertThat(call(before)).isEqualTo(ORDERS + " " + first.leaf().getSerialNumber());

            var second = ca.issue(ORDERS.toString(), Duration.ofMinutes(5));
            keys.update(second.privateKey(), second.chain());
            // without this, the JDK may resume the earlier TLS session and the server keeps seeing the first SVID
            // (verify/run.sh measures how long that can last); a full handshake presents the new one
            before.getSession().invalidate();
            try (SSLSocket after = SpiffeTls.connect(ctx, server.getLocalPort())) {
                assertThat(call(after)).isEqualTo(ORDERS + " " + second.leaf().getSerialNumber());
            }
            // the connection made with the first SVID is unaffected: certificates are checked at the handshake only
            var in = new BufferedReader(new InputStreamReader(before.getInputStream(), StandardCharsets.UTF_8));
            before.getOutputStream().write("again\n".getBytes(StandardCharsets.UTF_8));
            before.getOutputStream().flush();
            assertThat(in.readLine()).isEqualTo(ORDERS + " " + first.leaf().getSerialNumber());
        }
    }

    @Test
    void aClientRefusesAServerWithTheWrongIdentity() throws Exception {
        var svid = ca.issue(ORDERS.toString(), Duration.ofMinutes(5));
        var keys = new RotatingKeyManager(svid.privateKey(), svid.chain());
        X509TrustManager expectsRiders = SpiffeTrustManager.expecting(bundles,
                SpiffeId.parse("spiffe://foodfleet.internal/ns/riders/sa/rider-service"), rejections::add);
        assertThatThrownBy(() -> SpiffeTls.connect(SpiffeTls.context(keys, expectsRiders), server.getLocalPort()))
                .isInstanceOf(IOException.class);
        assertThat(rejections).contains("peer " + PAYMENTS + " is not authorized");
    }
}
