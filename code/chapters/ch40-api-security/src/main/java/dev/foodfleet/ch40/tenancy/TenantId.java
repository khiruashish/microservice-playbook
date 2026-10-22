package dev.foodfleet.ch40.tenancy;

import java.util.regex.Pattern;

/**
 * A tenant: a restaurant group or a country business on the shared platform ("foodfleet-in", "spice-route").
 * Comes from the verified access token (a claim the authorization server sets), never from a header or a path
 * segment the client chose.
 */
public record TenantId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9-]{1,38}[a-z0-9]");

    public TenantId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a tenant id");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
