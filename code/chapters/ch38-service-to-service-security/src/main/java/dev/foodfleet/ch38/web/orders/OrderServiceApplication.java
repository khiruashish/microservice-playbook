package dev.foodfleet.ch38.web.orders;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** order-service's side of the call: configured by order-service.yml (SSL bundle, client registration, base URL). */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OrderServiceApplication.class).properties("spring.config.name=order-service").run(args);
    }
}
