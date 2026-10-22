package dev.foodfleet.ch38.spiffe;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Accepts a peer only if it presents an X.509-SVID that (1) follows the SVID rules, (2) chains to the trust bundle
 * of the trust domain in its own SPIFFE ID, and (3) is authorized: the server side usually accepts any member of
 * the trusted domains and leaves per-route decisions to the application; the client side names the one server
 * identity it expects (SPIFFE replaces hostname verification). Bundles can be swapped at runtime for CA rotation.
 */
public final class SpiffeTrustManager extends X509ExtendedTrustManager {

    private final AtomicReference<TrustBundles> bundles;
    private final Predicate<SpiffeId> authorizer;
    private final Consumer<String> rejections;

    public SpiffeTrustManager(TrustBundles bundles, Predicate<SpiffeId> authorizer, Consumer<String> rejections) {
        this.bundles = new AtomicReference<>(bundles);
        this.authorizer = authorizer;
        this.rejections = rejections;
    }

    /** Any SVID from a trusted domain: for servers that authorize per route after the handshake. */
    public static SpiffeTrustManager anyMember(TrustBundles bundles, Consumer<String> rejections) {
        return new SpiffeTrustManager(bundles, id -> true, rejections);
    }

    /** Exactly one expected peer: for clients calling a known service. */
    public static SpiffeTrustManager expecting(TrustBundles bundles, SpiffeId peer, Consumer<String> rejections) {
        return new SpiffeTrustManager(bundles, peer::equals, rejections);
    }

    public void updateBundles(TrustBundles next) {
        bundles.set(next);
    }

    public SpiffeId verify(X509Certificate[] chain) throws SvidRejected {
        try {
            if (chain == null || chain.length == 0) {
                throw new SvidRejected("no certificate presented");
            }
            SpiffeId id = X509Svid.spiffeId(chain[0]);
            Set<TrustAnchor> anchors = bundles.get().anchorsFor(id.trustDomain());
            if (anchors.isEmpty()) {
                throw new SvidRejected("no trust bundle for trust domain " + id.trustDomain());
            }
            validatePath(chain, anchors);
            if (!authorizer.test(id)) {
                throw new SvidRejected("peer " + id + " is not authorized");
            }
            return id;
        } catch (SvidRejected e) {
            rejections.accept(e.getMessage());
            throw e;
        }
    }

    private static void validatePath(X509Certificate[] chain, Set<TrustAnchor> anchors) throws SvidRejected {
        try {
            // the path is the leaf plus intermediates; anything the peer sends that is itself an anchor is dropped
            var certs = Arrays.stream(chain)
                    .filter(c -> anchors.stream().noneMatch(a -> a.getTrustedCert().equals(c)))
                    .toList();
            CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(certs);
            PKIXParameters params = new PKIXParameters(anchors);
            // revocation: SVIDs live for an hour or less, so expiry does the job CRLs and OCSP do for long-lived certs
            params.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(path, params);
        } catch (CertPathValidatorException e) {
            throw new SvidRejected("path validation failed: " + e.getMessage(), e);
        } catch (GeneralSecurityException e) {
            throw new SvidRejected("cannot validate path: " + e.getMessage(), e);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws SvidRejected {
        verify(chain);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws SvidRejected {
        verify(chain);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws SvidRejected {
        verify(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws SvidRejected {
        verify(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws SvidRejected {
        verify(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws SvidRejected {
        verify(chain);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return bundles.get().allCertificates();
    }
}
