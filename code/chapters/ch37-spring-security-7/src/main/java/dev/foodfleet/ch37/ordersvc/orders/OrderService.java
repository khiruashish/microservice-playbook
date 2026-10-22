package dev.foodfleet.ch37.ordersvc.orders;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Method security on the service, so every caller of these methods (controllers, listeners, jobs) is checked.
 * A denied read surfaces as 403 from Spring Security; chapter 35 explains why a 404 is often better, which
 * {@link OrderController} does for reads.
 */
@Service
public class OrderService {

    private final OrderStore store;

    public OrderService(OrderStore store) {
        this.store = store;
    }

    @PreAuthorize("@orderAccess.canRead(authentication, #id)")
    public Order get(long id) {
        return store.find(id).orElseThrow();
    }

    @PreAuthorize("@orderAccess.canRefund(authentication, #id)")
    public Order refund(long id) {
        Order o = store.find(id).orElseThrow();
        Order refunded = new Order(o.id(), o.customerId(), o.restaurantId(), "REFUNDED", o.amountPaise());
        store.save(refunded);
        return refunded;
    }
}
