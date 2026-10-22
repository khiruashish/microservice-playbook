package dev.foodfleet.ch01.modulith.notification.internal;

import dev.foodfleet.ch01.modulith.order.OrderPlaced;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Notification has no public API at all. It only reacts to events, so adding it required
 * zero changes to the order module. Compare with the layered monolith, where OrderService
 * had to be edited to call NotificationService.
 */
@Component
class OrderNotificationListener {

    private final NotificationSender sender;

    OrderNotificationListener(NotificationSender sender) {
        this.sender = sender;
    }

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        String message = "Your order %s is placed (₹%d.%02d). We'll tell you when the kitchen starts cooking."
                .formatted(event.orderId(), event.totalPaise() / 100, event.totalPaise() % 100);
        sender.send(event.customerId(), message);
    }
}
