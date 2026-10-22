package dev.foodfleet.ch37.ordersvc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenClaimsTest {

    @Test
    void keycloakTokensCarryRealmRolesAndASpaceSeparatedScope() {
        Map<String, Object> keycloak = Map.of(
                "scope", "openid orders:read",
                "realm_access", Map.of("roles", List.of("CUSTOMER", "offline_access", "default-roles-foodfleet")));
        assertThat(TokenClaims.authorities(keycloak)).isEqualTo(Set.of("SCOPE_openid", "SCOPE_orders:read", "ROLE_CUSTOMER"));
    }

    @Test
    void springAuthorizationServerTokensCarryAScopeArrayAndARolesClaim() {
        Map<String, Object> spring = Map.of("scope", List.of("orders:read", "orders:write"), "roles", List.of("SUPPORT"));
        assertThat(TokenClaims.authorities(spring)).isEqualTo(Set.of("SCOPE_orders:read", "SCOPE_orders:write", "ROLE_SUPPORT"));
    }

    @Test
    void onlyFoodFleetRolesBecomeAuthorities() {
        assertThat(TokenClaims.authorities(Map.of("roles", List.of("admin", "ROOT", "ADMIN")))).isEqualTo(Set.of("ROLE_ADMIN"));
        assertThat(TokenClaims.authorities(Map.of())).isEqualTo(Set.of());
    }

    @Test
    void restaurantIdsAreReadAsNumbersOrDigitStrings() {
        assertThat(TokenClaims.restaurants(Map.of("restaurants", List.of(7L, "9", "x")))).isEqualTo(Set.of(7L, 9L));
    }
}
