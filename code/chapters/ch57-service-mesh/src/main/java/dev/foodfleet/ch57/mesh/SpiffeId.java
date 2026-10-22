package dev.foodfleet.ch57.mesh;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A workload identity in SPIFFE form, {@code spiffe://<trust domain>/ns/<namespace>/sa/<service account>}, as Istio and
 * Linkerd put it in the URI SAN of each sidecar's certificate. Authorization decisions use this, not IP addresses.
 */
public record SpiffeId(String trustDomain, String namespace, String serviceAccount) {

    private static final Pattern FORM = Pattern.compile("spiffe://([a-z0-9.-]+)/ns/([a-z0-9-]+)/sa/([a-z0-9-]+)");

    public SpiffeId {
        if (!FORM.matcher("spiffe://" + trustDomain + "/ns/" + namespace + "/sa/" + serviceAccount).matches()) {
            throw new IllegalArgumentException("not a valid identity: " + trustDomain + "/" + namespace + "/" + serviceAccount);
        }
    }

    public static SpiffeId parse(String uri) {
        Matcher m = FORM.matcher(uri);
        if (!m.matches()) {
            throw new IllegalArgumentException("not a SPIFFE workload id: " + uri);
        }
        return new SpiffeId(m.group(1), m.group(2), m.group(3));
    }

    @Override
    public String toString() {
        return "spiffe://" + trustDomain + "/ns/" + namespace + "/sa/" + serviceAccount;
    }
}
