package dev.foodfleet.ch37.ordersvc.orders;

import dev.foodfleet.ch37.ordersvc.security.TokenClaims;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Object-level checks for {@code @PreAuthorize}, e.g. {@code @orderAccess.canRead(authentication, #id)}. The
 * order is loaded here, from the store, never taken from the request.
 */
@Component("orderAccess")
public class OrderAccess {

    private final OrderStore store;

    public OrderAccess(OrderStore store) {
        this.store = store;
    }

    public boolean canRead(Authentication auth, long id) {
        return store.find(id).map(o -> OrderRules.canRead(auth.getName(), authorities(auth), restaurants(auth), o))
                .orElse(false);
    }

    public boolean canChange(Authentication auth, long id) {
        return store.find(id).map(o -> OrderRules.canChange(auth.getName(), authorities(auth), o)).orElse(false);
    }

    public boolean canRefund(Authentication auth, long id) {
        return store.find(id).map(o -> OrderRules.canRefund(authorities(auth), o)).orElse(false);
    }

    static Set<String> authorities(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    static Set<Long> restaurants(Authentication auth) {
        Map<String, Object> claims = auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getClaims() : Map.of();
        return TokenClaims.restaurants(claims);
    }
}
