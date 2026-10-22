package dev.foodfleet.ch38.web.payments;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch38.pki.DevCa;
import dev.foodfleet.ch38.spiffe.SpiffeId;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The filter as the servlet container drives it, with the client certificate chain Tomcat puts on the request. */
class PeerPolicyFilterTest {

    private final DevCa ca = DevCa.create("foodfleet.internal", Duration.ofHours(1));
    private final PeerPolicyFilter filter = new PeerPolicyFilter(new PaymentSecurityConfig().peerPolicy(), Clock.systemUTC());

    private MockHttpServletResponse call(String method, String uri, X509Certificate... chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (chain.length > 0) {
            request.setAttribute("jakarta.servlet.request.X509Certificate", chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain next = new MockFilterChain();
        filter.doFilter(request, response, next);
        if (next.getRequest() != null) {
            response.setStatus(200);                // reached the next filter: the token checks come next
            assertThat(request.getAttribute(PeerPolicyFilter.PEER_ATTRIBUTE)).isInstanceOf(SpiffeId.class);
        }
        return response;
    }

    private X509Certificate svid(String id) {
        return ca.issue(id, Duration.ofMinutes(5)).leaf();
    }

    @Test
    void orderServiceMayCaptureButNotRunSettlements() throws Exception {
        X509Certificate orders = svid("spiffe://foodfleet.internal/ns/orders/sa/order-service");
        assertThat(call("POST", "/payments/42/capture", orders).getStatus()).isEqualTo(200);
        assertThat(call("POST", "/internal/settlements/run", orders).getStatus()).isEqualTo(403);
    }

    @Test
    void otherWorkloadsAndUnknownRoutesAreForbidden() throws Exception {
        X509Certificate riders = svid("spiffe://foodfleet.internal/ns/riders/sa/rider-service");
        assertThat(call("POST", "/payments/42/capture", riders).getStatus()).isEqualTo(403);
        X509Certificate orders = svid("spiffe://foodfleet.internal/ns/orders/sa/order-service");
        assertThat(call("GET", "/payments/42", orders).getStatus()).isEqualTo(403);
    }

    @Test
    void noCertificateOrAnExpiredOneIsForbidden() throws Exception {
        assertThat(call("POST", "/payments/42/capture").getStatus()).isEqualTo(403);
        X509Certificate orders = svid("spiffe://foodfleet.internal/ns/orders/sa/order-service");
        var later = new PeerPolicyFilter(new PaymentSecurityConfig().peerPolicy(),
                Clock.fixed(Instant.now().plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments/42/capture");
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[] {orders});
        MockHttpServletResponse response = new MockHttpServletResponse();
        later.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
