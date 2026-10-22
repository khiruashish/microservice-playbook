package dev.foodfleet.ch29;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Two FoodFleet services in one module for brevity: the catalog (PostgreSQL, with a Redis price cache fed
 * from an outbox) and rider locations (Redis for the live position, PostgreSQL for the history). In
 * production they are separate deployables, each with its own database (chapter 12).
 */
@SpringBootApplication
@EnableScheduling
public class PolyglotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolyglotApplication.class, args);
    }
}
