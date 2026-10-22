package dev.foodfleet.ch37.authserver;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * FoodFleet's authorization server for local development, on Spring Security 7's built-in OAuth 2.1 Authorization
 * Server (formerly the separate Spring Authorization Server project). Clients are configured in authserver.yml;
 * Spring Boot builds the registered clients, the JWK source and the security filter chains from it.
 * Production runs Keycloak (keycloak/foodfleet-realm.json) or a managed provider.
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(AuthServerApplication.class).properties("spring.config.name=authserver").run(args);
    }
}
