package dev.foodfleet.ch11;

import dev.foodfleet.ch11.kitchen.KitchenCapacityLookup;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;

/**
 * Calls kitchen-service a few times through client-side discovery and prints which instance
 * answered. Start two kitchen stubs on 8091 and 8093 (see README), or run the tests.
 */
@SpringBootApplication
public class DiscoveryDemoApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DiscoveryDemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Bean
    @ConditionalOnBooleanProperty(name = "foodfleet.demo.run", matchIfMissing = true)
    ApplicationRunner demo(KitchenCapacityLookup kitchens) {
        return args -> {
            for (int i = 0; i < 6; i++) {
                System.out.println("answered by " + kitchens.capacity().instance());
            }
        };
    }
}
