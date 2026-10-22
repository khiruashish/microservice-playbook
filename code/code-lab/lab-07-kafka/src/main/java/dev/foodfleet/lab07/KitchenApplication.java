package dev.foodfleet.lab07;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** kitchen-service, with order-service's publishing side in the same lab for convenience. */
@SpringBootApplication
public class KitchenApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenApplication.class, args);
    }
}
