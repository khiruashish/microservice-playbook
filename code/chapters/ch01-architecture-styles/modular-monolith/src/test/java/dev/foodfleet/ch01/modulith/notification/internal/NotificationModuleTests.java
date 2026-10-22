package dev.foodfleet.ch01.modulith.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch01.modulith.order.OrderPlaced;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

@ApplicationModuleTest
class NotificationModuleTests {

    private final NotificationSender sender;

    NotificationModuleTests(@Autowired NotificationSender sender) {
        this.sender = sender;
    }

    @Test
    void notifiesCustomerWhenOrderIsPlaced(Scenario scenario) {
        UUID orderId = UUID.randomUUID();

        scenario.publish(new OrderPlaced(orderId, "cust-99", "rest-n-1", 49_900, 1, Instant.now()))
                .andWaitForStateChange(sender::sentMessages, (List<String> messages) -> messages.stream()
                        .anyMatch(m -> m.contains(orderId.toString())))
                .andVerify(messages -> assertThat(messages)
                        .anySatisfy(m -> assertThat(m).startsWith("cust-99:").contains("₹499.00")));
    }
}
