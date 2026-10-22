package dev.foodfleet.ch40.csrf;

import dev.foodfleet.ch40.cors.CorsPolicy;
import java.util.Locale;
import java.util.Set;

/**
 * A resource isolation policy built on Fetch Metadata request headers ({@code Sec-Fetch-Site}, {@code -Mode},
 * {@code -Dest}), which browsers set themselves and pages cannot forge. For endpoints authenticated by cookies
 * (the backend-for-frontend), it refuses state-changing requests that another site started, before any
 * application code runs:
 * <ul>
 * <li>{@code same-origin} and {@code none} (typed URL, bookmark) are allowed</li>
 * <li>{@code same-site} is refused unless the Origin is trusted: a sibling subdomain (user content, a forgotten
 * marketing site) is same-site, and SameSite cookies do not protect against it</li>
 * <li>{@code cross-site} is refused, except a top-level GET navigation (someone following a link)</li>
 * <li>no {@code Sec-Fetch-Site} at all: an older browser or not a browser; fall back to checking Origin when one
 * is sent, and leave the rest to the CSRF token</li>
 * </ul>
 * This sits in front of the synchronizer token Spring Security checks, not instead of it.
 */
public final class FetchMetadataPolicy {

    public record Verdict(boolean allowed, String reason) {
    }

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    private final CorsPolicy trustedOrigins;

    public FetchMetadataPolicy(CorsPolicy trustedOrigins) {
        this.trustedOrigins = trustedOrigins;
    }

    public Verdict check(String method, String site, String mode, String dest, String origin) {
        String m = method.toUpperCase(Locale.ROOT);
        if (site == null) {
            if (origin != null && !SAFE.contains(m) && !trustedOrigins.allowsOrigin(origin)) {
                return new Verdict(false, "no fetch metadata; untrusted Origin");
            }
            return new Verdict(true, "no fetch metadata");
        }
        switch (site.toLowerCase(Locale.ROOT)) {
            case "same-origin", "none" -> {
                return new Verdict(true, site);
            }
            case "same-site" -> {
                if (origin != null && trustedOrigins.allowsOrigin(origin)) {
                    return new Verdict(true, "same-site, trusted origin");
                }
                return new Verdict(false, "same-site, untrusted origin");
            }
            case "cross-site" -> {
                if ("GET".equals(m) && "navigate".equalsIgnoreCase(mode) && !"object".equalsIgnoreCase(dest)
                        && !"embed".equalsIgnoreCase(dest)) {
                    return new Verdict(true, "cross-site navigation");
                }
                if (origin != null && trustedOrigins.allowsOrigin(origin)) {
                    return new Verdict(true, "cross-site, trusted origin");
                }
                return new Verdict(false, "cross-site");
            }
            default -> {
                return new Verdict(false, "unknown Sec-Fetch-Site");
            }
        }
    }
}
