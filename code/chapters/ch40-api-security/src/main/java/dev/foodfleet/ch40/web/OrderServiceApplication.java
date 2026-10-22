package dev.foodfleet.ch40.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * order-service's public edge (order-service.yml): a bearer-token JSON API under /api for the apps and partners,
 * a cookie-session backend-for-frontend under /bff for the web app, each with the defences its kind of client
 * needs. Rate limits per client and per tenant in Redis; tenant isolation down to PostgreSQL rows.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OrderServiceApplication.class).properties("spring.config.name=order-service").run(args);
    }
}
