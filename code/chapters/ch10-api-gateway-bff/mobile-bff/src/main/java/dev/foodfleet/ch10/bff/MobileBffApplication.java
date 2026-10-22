package dev.foodfleet.ch10.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Backend for the FoodFleet customer app. Owned by the app team; one endpoint per screen. */
@SpringBootApplication
public class MobileBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MobileBffApplication.class, args);
    }
}
