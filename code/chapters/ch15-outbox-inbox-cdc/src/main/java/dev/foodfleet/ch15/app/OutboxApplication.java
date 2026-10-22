package dev.foodfleet.ch15.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** ordering-service with a transactional outbox, a polling relay and (for loyalty) an inbox. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OutboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboxApplication.class, args);
    }
}
