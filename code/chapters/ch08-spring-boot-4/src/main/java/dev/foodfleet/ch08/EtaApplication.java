package dev.foodfleet.ch08;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** FoodFleet eta-service: "when will my food arrive?" */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EtaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtaApplication.class, args);
    }
}
