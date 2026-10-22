package dev.foodfleet.ch01.modulith.notification.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stand-in for an SMS / push gateway (FoodFleet's real one comes in the notification-service chapter). */
@Component
class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final List<String> sent = new CopyOnWriteArrayList<>();

    void send(String customerId, String message) {
        sent.add(customerId + ": " + message);
        log.info("notification.sent customerId={}", customerId);
    }

    List<String> sentMessages() {
        return List.copyOf(sent);
    }
}
