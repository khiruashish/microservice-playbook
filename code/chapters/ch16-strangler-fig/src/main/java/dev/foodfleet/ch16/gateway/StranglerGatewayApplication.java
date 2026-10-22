package dev.foodfleet.ch16.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** The strangler facade at the edge: everything goes to the monolith unless a route says otherwise. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StranglerGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(StranglerGatewayApplication.class, args);
    }
}
