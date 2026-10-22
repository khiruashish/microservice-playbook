package dev.foodfleet.ch15.orders;

/** Places and cancels orders, and must tell the rest of FoodFleet that it did. */
public interface OrderService {

    /** @throws Exception if the caller should see a failure (and may retry) */
    void place(String orderId, String customerId, long totalPaise) throws Exception;

    void cancel(String orderId) throws Exception;
}
