package dev.foodfleet.lab08;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The object-level rule, in one place (ADR-035): a customer reads their own orders; support reads any. Tested on its
 * own, and called from @PreAuthorize so no endpoint can forget it.
 */
@Component("orderAccess")
public class OrderAccess {

    private final Orders orders;

    public OrderAccess(Orders orders) {
        this.orders = orders;
    }

    public boolean canRead(Authentication caller, String orderId) {
        if (caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPPORT"))) {
            return true;
        }
        return orders.find(orderId).map(o -> o.customerId().equals(caller.getName())).orElse(false);
    }
}
