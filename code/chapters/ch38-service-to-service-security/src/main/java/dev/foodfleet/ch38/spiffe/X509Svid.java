package dev.foodfleet.ch38.spiffe;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The checks the X509-SVID standard (§5) requires of a certificate used for authentication, on top of ordinary
 * path validation: exactly one URI SAN, holding a SPIFFE ID with a non-root path; {@code cA=false}; key usage
 * present with {@code digitalSignature} and without {@code keyCertSign} or {@code cRLSign}.
 * Ordinary TLS trust managers check none of these.
 */
public final class X509Svid {

    private static final int URI_NAME = 6;

    private X509Svid() {
    }

    /** The SPIFFE ID of a leaf SVID, or {@link SvidRejected} saying which rule it breaks. */
    public static SpiffeId spiffeId(X509Certificate leaf) throws SvidRejected {
        List<String> uris = uriSans(leaf);
        if (uris.isEmpty()) {
            throw new SvidRejected("no URI SAN: not an X.509-SVID");
        }
        if (uris.size() > 1) {
            throw new SvidRejected("more than one URI SAN " + uris);
        }
        SpiffeId id;
        try {
            id = SpiffeId.parse(uris.getFirst());
        } catch (IllegalArgumentException e) {
            throw new SvidRejected("URI SAN is not a valid SPIFFE ID: " + e.getMessage());
        }
        if (id.path().isEmpty()) {
            throw new SvidRejected("SPIFFE ID " + id + " has no path: a trust domain's ID cannot authenticate");
        }
        if (leaf.getBasicConstraints() != -1) {
            throw new SvidRejected("certificate is a CA (cA=true): signing certificates must not authenticate");
        }
        boolean[] usage = leaf.getKeyUsage();
        if (usage == null) {
            throw new SvidRejected("key usage extension missing");
        }
        if (!usage[0]) {
            throw new SvidRejected("key usage lacks digitalSignature");
        }
        if ((usage.length > 5 && usage[5]) || (usage.length > 6 && usage[6])) {
            throw new SvidRejected("leaf may not have keyCertSign or cRLSign");
        }
        return id;
    }

    /**
     * For each request on an established connection: the SVID rules plus "is this certificate valid now?".
     * The handshake checked validity once; a long-lived connection, or a new one that resumed an earlier TLS session,
     * carries a certificate that may have expired since. Expiry is how short-lived SVIDs are revoked.
     */
    public static SpiffeId spiffeIdValidAt(X509Certificate leaf, java.time.Instant now) throws SvidRejected {
        SpiffeId id = spiffeId(leaf);
        if (now.isAfter(leaf.getNotAfter().toInstant())) {
            throw new SvidRejected("SVID of " + id + " expired at " + leaf.getNotAfter().toInstant());
        }
        if (now.isBefore(leaf.getNotBefore().toInstant())) {
            throw new SvidRejected("SVID of " + id + " not valid before " + leaf.getNotBefore().toInstant());
        }
        return id;
    }

    static List<String> uriSans(X509Certificate cert) throws SvidRejected {
        try {
            Collection<List<?>> names = cert.getSubjectAlternativeNames();
            List<String> uris = new ArrayList<>();
            if (names != null) {
                for (List<?> name : names) {
                    if (((Integer) name.get(0)) == URI_NAME) {
                        uris.add((String) name.get(1));
                    }
                }
            }
            return uris;
        } catch (CertificateParsingException e) {
            throw new SvidRejected("unreadable subject alternative names", e);
        }
    }
}
