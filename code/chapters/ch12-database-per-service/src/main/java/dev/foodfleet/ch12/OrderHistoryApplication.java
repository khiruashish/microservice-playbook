package dev.foodfleet.ch12;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** A composition endpoint: the customer's order history, joined from three services. */
@SpringBootApplication
public class OrderHistoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderHistoryApplication.class, args);
    }
}
