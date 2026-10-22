package dev.foodfleet.ch25.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * order-events consumers: kitchen tickets (ordered, blocking retries) and receipt emails
 * (non-blocking retry topics). The partner and mail clients are logging stand-ins here.
 */
@SpringBootApplication
public class MessagingApplication {

    private static final Logger log = LoggerFactory.getLogger(MessagingApplication.class);

    @Bean
    KitchenDisplay kitchenDisplay() {
        return event -> log.info("kitchen {} <- {} {}", event.restaurantId(), event.orderId(), event.type());
    }

    @Bean
    ReceiptEmailListener.Mailer mailer() {
        return event -> log.info("receipt sent for {}", event.orderId());
    }

    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
