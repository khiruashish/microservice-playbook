package dev.foodfleet.ch01.kitchenservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * L4 — the kitchen capability extracted into its own deployable. It has its own process, port,
 * release cadence and scaling policy. It serves the same HTTP contract the kitchen module served
 * inside the monolith, so the order module only had to switch adapters.
 */
@SpringBootApplication
public class KitchenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenServiceApplication.class, args);
    }
}
