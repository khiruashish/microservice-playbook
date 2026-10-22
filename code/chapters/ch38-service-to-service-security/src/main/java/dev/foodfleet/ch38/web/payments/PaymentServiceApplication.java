package dev.foodfleet.ch38.web.payments;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * payment-service over mutual TLS with its SPIFFE SVID (payment-service.yml): Spring Boot's PEM SSL bundle reads the
 * SVID, key and trust bundle that spiffe-helper writes from the SPIRE agent, and reloads them when they change.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(PaymentServiceApplication.class)
                .properties("spring.config.name=payment-service").run(args);
    }
}
