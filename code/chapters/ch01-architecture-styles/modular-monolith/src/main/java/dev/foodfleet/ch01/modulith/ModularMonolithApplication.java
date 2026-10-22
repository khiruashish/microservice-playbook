package dev.foodfleet.ch01.modulith;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * L2–L4 — the same FoodFleet capabilities as the layered monolith, organised as a modular monolith.
 *
 * <p>Each direct sub-package is an application module (Spring Modulith convention):
 * <pre>
 *   order         public API: OrderManagement, PlaceOrder, OrderPlaced, RestaurantAvailability (SPI)
 *   kitchen       public API: KitchenOperations, KitchenAvailability
 *   notification  no public API — reacts to events only
 * </pre>
 * Anything in an {@code internal} sub-package is invisible to other modules, and
 * {@code ModularityTests} fails the build if that rule — or the no-cycles rule — is broken.
 *
 * <p>{@code @EnableAsync} lets {@code @ApplicationModuleListener}s run on their own (virtual) threads
 * after the publishing transaction commits.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class ModularMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModularMonolithApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
