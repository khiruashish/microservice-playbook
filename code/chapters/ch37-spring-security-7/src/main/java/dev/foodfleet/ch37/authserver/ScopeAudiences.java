package dev.foodfleet.ch37.authserver;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which API a scope belongs to. Spring Security's authorization server sets an access token's audience to the
 * requesting client's id by default ({@code aud: customer-app}); resource servers that check their own audience
 * would reject every such token. FoodFleet sets the audience to the APIs the granted scopes are for.
 */
public final class ScopeAudiences {

    static final Map<String, String> PREFIX_TO_API = Map.of(
            "orders:", "order-service",
            "payments:", "payment-service",
            "menu:", "menu-service");

    private ScopeAudiences() {
    }

    public static List<String> forScopes(Set<String> scopes) {
        Set<String> out = new LinkedHashSet<>();
        for (String scope : scopes.stream().sorted().toList()) {
            PREFIX_TO_API.forEach((prefix, api) -> {
                if (scope.startsWith(prefix)) {
                    out.add(api);
                }
            });
        }
        return List.copyOf(out);
    }
}
