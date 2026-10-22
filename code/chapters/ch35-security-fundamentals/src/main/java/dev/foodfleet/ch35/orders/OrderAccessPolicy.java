package dev.foodfleet.ch35.orders;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.Role;
import java.util.EnumSet;
import java.util.Set;

/**
 * Who may do what to which order, in one place (API1: object level, API5: function level authorization). Every
 * operation on an order asks this policy with the authenticated caller and the stored order; the request's ids
 * only say which order is meant, never who may touch it.
 */
public final class OrderAccessPolicy {

    public enum Action { READ, UPDATE_DETAILS, CANCEL, REFUND, EXPORT_ALL }

    private static final Set<OrderStatus> CHANGEABLE = EnumSet.of(OrderStatus.PLACED, OrderStatus.PREPARING);

    public boolean allows(Caller caller, Action action, Order order) {
        return switch (action) {
            case READ -> isCustomer(caller, order) || ownsRestaurant(caller, order) || isRider(caller, order)
                    || caller.has(Role.SUPPORT) || caller.has(Role.ADMIN);
            case UPDATE_DETAILS, CANCEL -> isCustomer(caller, order) && CHANGEABLE.contains(order.status());
            case REFUND -> (caller.has(Role.SUPPORT) || caller.has(Role.ADMIN))
                    && order.status() == OrderStatus.DELIVERED;
            case EXPORT_ALL -> caller.has(Role.ADMIN);
        };
    }

    /** For functions that aren't about one order. */
    public boolean allows(Caller caller, Action action) {
        return action == Action.EXPORT_ALL && caller.has(Role.ADMIN);
    }

    private static boolean isCustomer(Caller caller, Order order) {
        return caller.has(Role.CUSTOMER) && caller.id().equals(order.customerId());
    }

    private static boolean ownsRestaurant(Caller caller, Order order) {
        return caller.has(Role.RESTAURANT_OWNER) && caller.restaurantIds().contains(order.restaurantId());
    }

    private static boolean isRider(Caller caller, Order order) {
        return caller.has(Role.RIDER) && caller.id().equals(order.riderId());
    }
}
