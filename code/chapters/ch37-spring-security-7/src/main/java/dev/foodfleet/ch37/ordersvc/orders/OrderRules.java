package dev.foodfleet.ch37.ordersvc.orders;

import java.util.Set;

/**
 * Who may do what to an order, as plain Java over the caller's id, authorities and restaurants, so it can be
 * tested exhaustively without Spring (chapter 35's approach). {@link OrderAccess} adapts it to method security.
 */
public final class OrderRules {

    private OrderRules() {
    }

    public static boolean canRead(String caller, Set<String> authorities, Set<Long> restaurants, Order o) {
        return isCustomer(caller, authorities, o)
                || (authorities.contains("ROLE_RESTAURANT_OWNER") && restaurants.contains(o.restaurantId()))
                || authorities.contains("ROLE_SUPPORT") || authorities.contains("ROLE_ADMIN");
    }

    public static boolean canChange(String caller, Set<String> authorities, Order o) {
        return isCustomer(caller, authorities, o) && Set.of("PLACED", "PREPARING").contains(o.status());
    }

    public static boolean canRefund(Set<String> authorities, Order o) {
        return (authorities.contains("ROLE_SUPPORT") || authorities.contains("ROLE_ADMIN"))
                && o.status().equals("DELIVERED");
    }

    private static boolean isCustomer(String caller, Set<String> authorities, Order o) {
        return authorities.contains("ROLE_CUSTOMER") && caller.equals(o.customerId());
    }
}
