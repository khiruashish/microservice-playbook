package dev.foodfleet.ch39.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * order-service with its secrets from OpenBao/Vault (order-service.yml): logs in with its Kubernetes service account,
 * reads its KV secrets as configuration, gets dynamic database credentials that rotate.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OrderServiceApplication.class).properties("spring.config.name=order-service").run(args);
    }
}
