package dev.foodfleet.lab08.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns access-token claims into authority names, for tokens from Keycloak or from Spring Authorization Server.
 * Scopes become {@code SCOPE_x} (what the client application may do); roles become {@code ROLE_X} (what the user
 * is), from Keycloak's {@code realm_access.roles} or a top-level {@code roles} claim. Only roles FoodFleet
 * defines are mapped: Keycloak adds its own ({@code offline_access}, {@code default-roles-...}) that mean nothing
 * here and shouldn't become authorities by accident.
 */
public final class TokenClaims {

    public static final Set<String> ROLES = Set.of("CUSTOMER", "RESTAURANT_OWNER", "RIDER", "SUPPORT", "ADMIN");

    private TokenClaims() {
    }

    public static Set<String> authorities(Map<String, Object> claims) {
        Set<String> out = new LinkedHashSet<>();
        Object scope = claims.get("scope");                        // Keycloak: "a b"; Spring's server: ["a","b"]
        if (scope instanceof String s) {
            for (String x : s.trim().split("\\s+")) {
                if (!x.isEmpty()) {
                    out.add("SCOPE_" + x);
                }
            }
        } else if (scope instanceof Collection<?> c) {
            c.forEach(x -> out.add("SCOPE_" + x));
        }
        for (String role : roles(claims)) {
            if (ROLES.contains(role)) {
                out.add("ROLE_" + role);
            }
        }
        return out;
    }

    /** Restaurant ids a restaurant owner's token carries (a custom claim set by the identity provider). */
    public static Set<Long> restaurants(Map<String, Object> claims) {
        Set<Long> out = new LinkedHashSet<>();
        if (claims.get("restaurants") instanceof Collection<?> c) {
            for (Object o : c) {
                if (o instanceof Number n) {
                    out.add(n.longValue());
                } else if (o instanceof String s && s.matches("\\d{1,18}")) {
                    out.add(Long.parseLong(s));
                }
            }
        }
        return out;
    }

    private static List<String> roles(Map<String, Object> claims) {
        Object realm = claims.get("realm_access");
        if (realm instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> r) {
            return r.stream().map(String::valueOf).toList();
        }
        if (claims.get("roles") instanceof Collection<?> r) {
            return r.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
