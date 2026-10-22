package dev.foodfleet.ch09.payment.config;

import dev.foodfleet.ch09.payment.adapter.out.persistence.JdbcPaymentRepository;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway;
import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import dev.foodfleet.ch09.payment.application.service.PaymentService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The composition root: the only place that knows which adapter implements which port. Everything
 * else depends on interfaces. Replace one {@code @Bean} and the core runs against a different world.
 */
@Configuration(proxyBeanMethods = false)
class PaymentConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PaymentRepository paymentRepository(JdbcClient jdbc, TransactionTemplate tx) {
        return new JdbcPaymentRepository(jdbc, tx);
    }

    /** One object implements all four driving ports; it's injected by port type. */
    @Bean
    PaymentService paymentService(PaymentRepository payments, PaymentGateway gateway, Clock clock) {
        return new PaymentService(payments, gateway, clock);
    }
}
