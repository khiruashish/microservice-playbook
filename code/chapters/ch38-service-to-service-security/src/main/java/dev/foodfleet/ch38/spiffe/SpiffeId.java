package dev.foodfleet.ch38.spiffe;

/**
 * A SPIFFE ID, parsed strictly by the rules of the SPIFFE-ID standard (§2): scheme {@code spiffe}; a lowercase trust
 * domain of {@code [a-z0-9.-_]}; no userinfo, port, query, fragment or percent-encoding; path segments of
 * {@code [a-zA-Z0-9.-_]}, none empty, {@code .} or {@code ..}, no trailing slash; at most 2048 bytes.
 * Two IDs are equal only if their strings are equal: no normalisation, so no two spellings of one identity.
 */
public record SpiffeId(String trustDomain, String path) {

    private static final String PREFIX = "spiffe://";

    public SpiffeId {
        checkTrustDomain(trustDomain);
        checkPath(path);
    }

    public static SpiffeId parse(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("empty SPIFFE ID");
        }
        if (id.length() > 2048) {
            throw new IllegalArgumentException("SPIFFE ID longer than 2048 bytes");
        }
        if (!id.startsWith(PREFIX)) {
            throw new IllegalArgumentException("scheme must be spiffe: " + id);
        }
        String rest = id.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        String td = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "" : rest.substring(slash);
        return new SpiffeId(td, path);
    }

    /** {@code spiffe://<trustDomain>/ns/<namespace>/sa/<serviceAccount>}: the shape SPIRE and Istio use on Kubernetes. */
    public static SpiffeId ofWorkload(String trustDomain, String namespace, String serviceAccount) {
        return new SpiffeId(trustDomain, "/ns/" + namespace + "/sa/" + serviceAccount);
    }

    public boolean isMemberOf(String domain) {
        return trustDomain.equals(domain);
    }

    @Override
    public String toString() {
        return PREFIX + trustDomain + path;
    }

    private static void checkTrustDomain(String td) {
        if (td == null || td.isEmpty()) {
            throw new IllegalArgumentException("trust domain is empty");
        }
        if (td.length() > 255) {
            throw new IllegalArgumentException("trust domain longer than 255 bytes");
        }
        for (char c : td.toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            if (!ok) {
                // catches uppercase, ':' (port), '@' (userinfo), '%' (encoding), '?' and '#'
                throw new IllegalArgumentException("trust domain may contain only [a-z0-9.-_], found '" + c + "'");
            }
        }
    }

    private static void checkPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        if (path.isEmpty()) {
            return;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with /");
        }
        if (path.endsWith("/")) {
            throw new IllegalArgumentException("path must not end with /");
        }
        for (String segment : path.substring(1).split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("empty, . or .. path segment in " + path);
            }
            for (char c : segment.toCharArray()) {
                boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                        || c == '.' || c == '-' || c == '_';
                if (!ok) {
                    throw new IllegalArgumentException("path may contain only [a-zA-Z0-9.-_], found '" + c + "'");
                }
            }
        }
    }
}
