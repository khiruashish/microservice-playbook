package dev.foodfleet.ch35.orders;

/**
 * The order as the order service stores it, including fields no client may see (risk score, payment token) or
 * change (status, amount, owner). It is never serialised to a client or bound from a request: clients get an
 * {@link OrderView} and send an {@link OrderPatch}.
 */
public record Order(long id, String customerId, long restaurantId, String riderId, OrderStatus status,
                    long amountPaise, String deliveryAddress, String contactPhone, String deliveryNote,
                    int riskScore, String paymentToken) {

    Order withStatus(OrderStatus s) {
        return new Order(id, customerId, restaurantId, riderId, s, amountPaise, deliveryAddress, contactPhone,
                deliveryNote, riskScore, paymentToken);
    }

    Order withChanges(OrderPatch patch) {
        return new Order(id, customerId, restaurantId, riderId, status, amountPaise, deliveryAddress,
                patch.contactPhone() != null ? patch.contactPhone() : contactPhone,
                patch.deliveryNote() != null ? patch.deliveryNote() : deliveryNote, riskScore, paymentToken);
    }
}
