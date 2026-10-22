package dev.foodfleet.ch14.choreography;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch14.placeorder.Faults.Fault;
import dev.foodfleet.ch14.placeorder.PlaceOrderSaga.Request;
import dev.foodfleet.ch14.placeorder.ScriptedFaults;
import dev.foodfleet.ch14.placeorder.Services;
import org.junit.jupiter.api.Test;

class ChoreographyTest {

    final ScriptedFaults faults = new ScriptedFaults();
    final Services.Orders orders = new Services.Orders(faults);
    final Services.Coupons coupons = new Services.Coupons(faults, 10);
    final Services.Payments payments = new Services.Payments(faults, true);
    final EventBus bus = new EventBus(3);
    final ChoreographedPlaceOrder flow = new ChoreographedPlaceOrder(bus, orders, coupons, payments,
            new Services.Kitchen(faults), new Services.Delivery(faults));

    @Test
    void theFlowOnlyExistsInTheTrace() {
        flow.placeOrder("s-1", new Request("o-1", 500_00, true), orders);

        assertThat(bus.trace()).containsExactly(
                "OrderCreated", "CouponReserved", "PaymentAuthorized", "TicketAccepted", "OrderApproved");
        assertThat(orders.status).containsEntry("o-1", Services.Orders.Status.APPROVED);
    }

    @Test
    void kitchenRejectionCompensatesAcrossThreeServices() {
        faults.on("kitchen.accept", Fault.REJECT);

        flow.placeOrder("s-1", new Request("o-1", 500_00, true), orders);

        assertThat(bus.trace()).containsExactly("OrderCreated", "CouponReserved", "PaymentAuthorized",
                "TicketRejected", "PaymentVoided", "CouponReleased", "OrderRejected");
        assertThat(payments.heldPaise).hasValue(0);
        assertThat(coupons.remainingUses).hasValue(10);
    }

    @Test
    void transientFailuresAreRedelivered_untilTheyAreNot() {
        faults.on("delivery.request-rider", Fault.TRANSIENT, Fault.TRANSIENT, Fault.TRANSIENT);

        flow.placeOrder("s-1", new Request("o-1", 500_00, false), orders);

        assertThat(bus.trace()).contains("DEAD-LETTER(TicketAccepted)");
        assertThat(orders.status).containsEntry("o-1", Services.Orders.Status.APPROVED);   // nobody else noticed
    }
}
