package dev.foodfleet.ch38.web.payments;

import dev.foodfleet.ch38.authz.PeerPolicy;
import dev.foodfleet.ch38.spiffe.SpiffeId;
import dev.foodfleet.ch38.spiffe.SvidRejected;
import dev.foodfleet.ch38.spiffe.X509Svid;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Which workload is calling, and may it call this route? Tomcat has already required a client certificate that
 * chains to the SPIRE trust bundle (server.ssl.client-auth=need); a standard trust manager checks nothing
 * SPIFFE-specific, so this filter applies the X509-SVID rules, checks the certificate is valid now (connections
 * and resumed TLS sessions outlive certificates), and asks the {@link PeerPolicy}. Runs before the bearer token
 * filter: the token says what may be done for whom, this says who is asking.
 */
public final class PeerPolicyFilter extends OncePerRequestFilter {

    public static final String PEER_ATTRIBUTE = "dev.foodfleet.ch38.web.payments.PeerPolicyFilter.peer";
    private static final String CERTIFICATES = "jakarta.servlet.request.X509Certificate";
    private static final Log log = LogFactory.getLog(PeerPolicyFilter.class);

    private final PeerPolicy policy;
    private final Clock clock;

    public PeerPolicyFilter(PeerPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        X509Certificate[] certificates = (X509Certificate[]) request.getAttribute(CERTIFICATES);
        if (certificates == null || certificates.length == 0) {
            deny(response, "no client certificate");
            return;
        }
        SpiffeId peer;
        try {
            peer = X509Svid.spiffeIdValidAt(certificates[0], clock.instant());
        } catch (SvidRejected e) {
            deny(response, e.getMessage());
            return;
        }
        PeerPolicy.Decision decision = policy.decide(peer, request.getMethod(), request.getRequestURI());
        if (!decision.allowed()) {
            deny(response, decision.reason());
            return;
        }
        request.setAttribute(PEER_ATTRIBUTE, peer);
        chain.doFilter(request, response);
    }

    private static void deny(HttpServletResponse response, String reason) throws IOException {
        log.warn("peer denied: " + reason);                  // the reason is for our logs, not for the caller
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
