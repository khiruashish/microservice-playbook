package dev.foodfleet.ch21;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** order-history-service: a customer's past orders, read-mostly, paged, cacheable. */
@SpringBootApplication
public class OrderHistoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderHistoryApplication.class, args);
    }
}
