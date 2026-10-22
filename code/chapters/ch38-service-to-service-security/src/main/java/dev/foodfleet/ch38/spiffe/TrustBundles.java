package dev.foodfleet.ch38.spiffe;

import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The CA certificates trusted for each trust domain: FoodFleet's own and any federated ones. A peer is validated
 * only against the bundle of the trust domain in its SPIFFE ID, so a partner's CA can never vouch for a
 * {@code foodfleet.internal} identity. Immutable; rotating a CA means publishing a new instance that holds the old
 * and the new CA until every SVID signed by the old one has expired.
 */
public final class TrustBundles {

    private final Map<String, Set<TrustAnchor>> anchors;

    private TrustBundles(Map<String, Set<TrustAnchor>> anchors) {
        this.anchors = anchors;
    }

    public static TrustBundles of(String trustDomain, X509Certificate... cas) {
        return new TrustBundles(Map.of()).with(trustDomain, cas);
    }

    /** A copy that trusts {@code cas} for {@code trustDomain} in place of what it trusted before. */
    public TrustBundles with(String trustDomain, X509Certificate... cas) {
        new SpiffeId(trustDomain, "");     // validates the trust domain name
        Set<TrustAnchor> set = new HashSet<>();
        for (X509Certificate ca : cas) {
            set.add(new TrustAnchor(ca, null));
        }
        Map<String, Set<TrustAnchor>> copy = new HashMap<>(anchors);
        copy.put(trustDomain, Set.copyOf(set));
        return new TrustBundles(Map.copyOf(copy));
    }

    public Set<TrustAnchor> anchorsFor(String trustDomain) {
        return anchors.getOrDefault(trustDomain, Set.of());
    }

    public X509Certificate[] allCertificates() {
        return anchors.values().stream().flatMap(Set::stream).map(TrustAnchor::getTrustedCert)
                .toArray(X509Certificate[]::new);
    }

    public List<String> trustDomains() {
        return anchors.keySet().stream().sorted().toList();
    }
}
