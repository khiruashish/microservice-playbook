package dev.foodfleet.ch19.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Both sides of the menu-items topic in one app, to keep the example small: catalog's publisher
 * and ordering's consumer. In FoodFleet they are separate services sharing only the topic and the
 * event's schema.
 */
@SpringBootApplication
public class EventDrivenApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventDrivenApplication.class, args);
    }
}
