package dev.foodfleet.ch42.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** order-service with deadlines, budgeted retries and bounded concurrency (order-service.yml). */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OrderServiceApplication.class).properties("spring.config.name=order-service").run(args);
    }
}
