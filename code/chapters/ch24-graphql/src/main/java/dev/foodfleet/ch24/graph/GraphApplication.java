package dev.foodfleet.ch24.graph;

import dev.foodfleet.ch24.experiment.FoodFleetBackends;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The customer app's GraphQL endpoint (POST /graphql). In FoodFleet it runs in the mobile BFF; the
 * services behind it are in-memory stand-ins here ({@link FoodFleetBackends}), in production HTTP
 * service clients (chapter 22) or gRPC stubs (chapter 23).
 */
@SpringBootApplication
public class GraphApplication {

    @Bean
    FoodFleetBackends services() {
        return new FoodFleetBackends();
    }

    public static void main(String[] args) {
        SpringApplication.run(GraphApplication.class, args);
    }
}
