package dev.foodfleet.ch02.menu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * FoodFleet menu-service. It has one responsibility: answer "what can I order from this restaurant,
 * and at what price right now?". It owns the menu catalogue. Surge pricing belongs to
 * pricing-service, which this service calls and must survive without.
 *
 * <pre>
 *   api       → HTTP contract (versioned)            depends on: browse
 *   browse    → use case: build a priced menu        depends on: catalog, pricing
 *   catalog   → menu data owned by this service      depends on: nothing
 *   pricing   → adapter to pricing-service + fallback depends on: nothing inside the service
 *   config    → wiring (clock, resilience, HTTP clients)
 * </pre>
 * {@code ArchitectureTest} enforces these arrows.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MenuServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MenuServiceApplication.class, args);
    }
}
