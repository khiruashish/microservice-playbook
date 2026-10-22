package dev.foodfleet.ch01.layered.service;

import dev.foodfleet.ch01.layered.domain.Order;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Sends the customer an order confirmation. Stands in for an SMS / push gateway. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<String> sent = new CopyOnWriteArrayList<>();

    public void orderConfirmed(Order order) {
        String message = "Hi %s, your order %s is confirmed (₹%d.%02d)".formatted(
                order.customerId(), order.id(), order.totalPaise() / 100, order.totalPaise() % 100);
        sent.add(message);
        log.info("notification.sent orderId={} customerId={}", order.id(), order.customerId());
    }

    public List<String> sentMessages() {
        return List.copyOf(sent);
    }
}
