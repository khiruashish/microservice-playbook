package dev.foodfleet.ch09.payment.adapter.in.events;

import dev.foodfleet.ch09.payment.application.port.in.RefundPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A second driving adapter on the same port: a message, not an HTTP request, triggers the use case.
 * The kitchen rejected the order, so whatever the customer paid is released.
 */
@Component
class KitchenRejectionListener {

    private static final Logger log = LoggerFactory.getLogger(KitchenRejectionListener.class);

    private final RefundPayment refunds;

    KitchenRejectionListener(RefundPayment refunds) {
        this.refunds = refunds;
    }

    @EventListener
    void on(KitchenRejectedOrder event) {
        refunds.releaseOrder(event.orderId(), "Kitchen rejected the order: " + event.reason())
                .ifPresentOrElse(
                        view -> log.info("Released payment {} for order {}: {}", view.paymentId(), event.orderId(), view.status()),
                        () -> log.info("Order {} holds no money; nothing to release", event.orderId()));
    }
}
