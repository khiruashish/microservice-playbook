package dev.foodfleet.ch35.orders;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.Role;
import dev.foodfleet.ch35.orders.OrderAccessPolicy.Action;
import java.util.List;
import java.util.function.Predicate;

/**
 * The order API's operations with the checks in the service, not the controller, so every entry point (REST, a
 * message listener, a batch job) gets them. An order the caller may not read is reported as not found: answering
 * 403 would confirm that order id exists.
 */
public class OrderService {

    private final OrderStore store;
    private final OrderAccessPolicy policy;
    private final PageLimits limits;

    public OrderService(OrderStore store, OrderAccessPolicy policy, PageLimits limits) {
        this.store = store;
        this.policy = policy;
        this.limits = limits;
    }

    public OrderView get(Caller caller, long id) {
        return OrderView.of(readable(caller, id), caller);
    }

    public OrderView update(Caller caller, long id, OrderPatch patch) {
        Order order = readable(caller, id);
        if (!policy.allows(caller, Action.UPDATE_DETAILS, order)) {
            throw new ForbiddenException("this order can no longer be changed by you");
        }
        Order changed = order.withChanges(patch);
        store.save(changed);
        return OrderView.of(changed, caller);
    }

    public OrderView refund(Caller caller, long id) {
        Order order = readable(caller, id);
        if (!policy.allows(caller, Action.REFUND, order)) {
            throw new ForbiddenException("refunds need the SUPPORT role and a delivered order");
        }
        Order refunded = order.withStatus(OrderStatus.REFUNDED);
        store.save(refunded);
        return OrderView.of(refunded, caller);
    }

    /** "My orders": the filter comes from who the caller is, never from a customerId parameter. */
    public List<OrderView> list(Caller caller, Integer page, Integer size) {
        int n = limits.size(size);
        int offset = limits.offset(page, n);
        Predicate<Order> scope;
        if (caller.has(Role.SUPPORT) || caller.has(Role.ADMIN)) {
            scope = o -> true;
        } else if (caller.has(Role.RESTAURANT_OWNER)) {
            scope = o -> caller.restaurantIds().contains(o.restaurantId());
        } else {
            scope = o -> caller.id().equals(o.customerId());
        }
        return store.page(scope, offset, n).stream().map(o -> OrderView.of(o, caller)).toList();
    }

    /** An admin function: the role check is here, not only on a hidden URL (API5). */
    public List<OrderView> exportAll(Caller caller) {
        if (!policy.allows(caller, Action.EXPORT_ALL)) {
            throw new ForbiddenException("export needs the ADMIN role");
        }
        return store.page(o -> true, 0, Integer.MAX_VALUE).stream().map(o -> OrderView.of(o, caller)).toList();
    }

    private Order readable(Caller caller, long id) {
        return store.find(id).filter(o -> policy.allows(caller, Action.READ, o))
                .orElseThrow(() -> new NotFoundException("order " + id + " not found"));
    }
}
