package dev.foodfleet.ch28.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** payment-service: {@code POST /payments} with Idempotency-Key, backed by PostgreSQL. */
@SpringBootApplication
public class PaymentsApplication {

    /** Stand-in for the payment provider's client; it honours the idempotency key we send it. */
    @Bean
    PaymentProvider paymentProvider() {
        return new InMemoryPaymentProvider();
    }

    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }
}
