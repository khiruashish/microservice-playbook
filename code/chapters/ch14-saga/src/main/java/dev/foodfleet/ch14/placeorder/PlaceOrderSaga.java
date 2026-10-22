package dev.foodfleet.ch14.placeorder;

import dev.foodfleet.ch14.saga.SagaDefinition;
import dev.foodfleet.ch14.saga.SagaStep;
import java.util.List;

/**
 * FoodFleet's place-order saga.
 *
 * <pre>
 *  #  step                 kind           compensation
 *  1  create order         compensatable  reject order
 *  2  reserve coupon       compensatable  release coupon
 *  3  authorize payment    compensatable  void authorization
 *  4  kitchen accepts      PIVOT          (food is being cooked: no going back)
 *  5  capture payment      retriable
 *  6  request rider        retriable
 *  7  approve order        retriable
 * </pre>
 *
 * Ordering matters: cheap, likely-to-fail checks first (coupon before payment); the step that
 * can't be undone (cooking) is the pivot; everything after it must eventually succeed.
 */
public final class PlaceOrderSaga {

    public record Request(String orderId, long amountPaise, boolean withCoupon) {
    }

    private PlaceOrderSaga() {
    }

    public static SagaDefinition<Request> definition(Services.Orders orders, Services.Coupons coupons,
                                                     Services.Payments payments, Services.Kitchen kitchen,
                                                     Services.Delivery delivery) {
        return new SagaDefinition<>("place-order", List.of(
                SagaStep.<Request>compensatable("create order",
                        (id, r) -> orders.createPending(id, r.orderId()),
                        (id, r) -> orders.reject(id, r.orderId())),
                SagaStep.<Request>compensatable("reserve coupon",
                        (id, r) -> {
                            if (r.withCoupon()) {
                                coupons.reserve(id);
                            }
                        },
                        (id, r) -> {
                            if (r.withCoupon()) {
                                coupons.release(id);
                            }
                        }),
                SagaStep.<Request>compensatable("authorize payment",
                        (id, r) -> payments.authorize(id, r.amountPaise()),
                        (id, r) -> payments.voidAuthorization(id, r.amountPaise())),
                SagaStep.<Request>pivot("kitchen accepts", (id, r) -> kitchen.accept(id)),
                SagaStep.<Request>retriable("capture payment", (id, r) -> payments.capture(id)),
                SagaStep.<Request>retriable("request rider", (id, r) -> delivery.requestRider(id)),
                SagaStep.<Request>retriable("approve order", (id, r) -> orders.approve(id, r.orderId()))));
    }
}
