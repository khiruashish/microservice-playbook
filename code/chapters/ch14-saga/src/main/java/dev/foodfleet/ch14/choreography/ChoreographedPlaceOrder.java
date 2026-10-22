package dev.foodfleet.ch14.choreography;

import dev.foodfleet.ch14.placeorder.PlaceOrderSaga.Request;
import dev.foodfleet.ch14.placeorder.Services;
import dev.foodfleet.ch14.saga.SagaFailure.BusinessRejection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The same place-order flow without an orchestrator: each service reacts to the previous
 * service's event and publishes its own. No component knows the whole flow; it exists only as
 * the sum of these subscriptions, and compensation is spread over several services' handlers.
 */
public final class ChoreographedPlaceOrder {

    private final EventBus bus;
    private final Map<String, Request> requests = new ConcurrentHashMap<>();

    public ChoreographedPlaceOrder(EventBus bus, Services.Orders orders, Services.Coupons coupons,
                                   Services.Payments payments, Services.Kitchen kitchen, Services.Delivery delivery) {
        this.bus = bus;

        // promotions-service
        bus.subscribe("OrderCreated", e -> {
            Request r = requests.get(e.sagaId());
            try {
                if (r.withCoupon()) {
                    coupons.reserve(e.sagaId());
                }
                bus.publish("CouponReserved", e.sagaId());
            } catch (BusinessRejection rejected) {
                bus.publish("CouponRejected", e.sagaId());
            }
        });
        // payment-service
        bus.subscribe("CouponReserved", e -> {
            try {
                payments.authorize(e.sagaId(), requests.get(e.sagaId()).amountPaise());
                bus.publish("PaymentAuthorized", e.sagaId());
            } catch (BusinessRejection rejected) {
                bus.publish("PaymentDeclined", e.sagaId());
            }
        });
        // kitchen-service
        bus.subscribe("PaymentAuthorized", e -> {
            try {
                kitchen.accept(e.sagaId());
                bus.publish("TicketAccepted", e.sagaId());
            } catch (BusinessRejection rejected) {
                bus.publish("TicketRejected", e.sagaId());
            }
        });
        // after the pivot: three services react to the same event
        bus.subscribe("TicketAccepted", e -> payments.capture(e.sagaId()));
        bus.subscribe("TicketAccepted", e -> delivery.requestRider(e.sagaId()));
        bus.subscribe("TicketAccepted", e -> {
            orders.approve(e.sagaId(), requests.get(e.sagaId()).orderId());
            bus.publish("OrderApproved", e.sagaId());
        });

        // compensations, each in the service that owns the data
        bus.subscribe("TicketRejected", e -> {
            payments.voidAuthorization(e.sagaId(), requests.get(e.sagaId()).amountPaise());
            bus.publish("PaymentVoided", e.sagaId());
        });
        bus.subscribe("PaymentVoided", e -> releaseCoupon(coupons, e.sagaId()));
        bus.subscribe("PaymentDeclined", e -> releaseCoupon(coupons, e.sagaId()));
        bus.subscribe("CouponReleased", e -> rejectOrder(orders, e.sagaId()));
        bus.subscribe("CouponRejected", e -> rejectOrder(orders, e.sagaId()));
    }

    private void releaseCoupon(Services.Coupons coupons, String sagaId) {
        if (requests.get(sagaId).withCoupon()) {
            coupons.release(sagaId);
        }
        bus.publish("CouponReleased", sagaId);
    }

    private void rejectOrder(Services.Orders orders, String sagaId) {
        orders.reject(sagaId, requests.get(sagaId).orderId());
        bus.publish("OrderRejected", sagaId);
    }

    /** ordering-service starts the flow. */
    public void placeOrder(String sagaId, Request request, Services.Orders orders) {
        requests.put(sagaId, request);
        orders.createPending(sagaId, request.orderId());
        bus.publish("OrderCreated", sagaId);
    }
}
