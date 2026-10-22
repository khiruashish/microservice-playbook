package dev.foodfleet.ch27.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** order-service's read API, with its contract (OpenAPI document) and consumer contracts in the build. */
@SpringBootApplication
public class ContractsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractsApplication.class, args);
    }
}
