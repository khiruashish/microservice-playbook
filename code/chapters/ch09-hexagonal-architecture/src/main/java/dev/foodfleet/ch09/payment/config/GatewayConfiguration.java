package dev.foodfleet.ch09.payment.config;

import dev.foodfleet.ch09.payment.adapter.out.gateway.FakePaymentGateway;
import dev.foodfleet.ch09.payment.adapter.out.gateway.PspClient;
import dev.foodfleet.ch09.payment.adapter.out.gateway.PspPaymentGateway;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** Which payment-gateway adapter plugs into the port is a deployment decision: {@code foodfleet.payment.gateway}. */
@Configuration(proxyBeanMethods = false)
class GatewayConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "foodfleet.payment.gateway", havingValue = "fake", matchIfMissing = true)
    static class Fake {

        @Bean
        PaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "foodfleet.payment.gateway", havingValue = "psp")
    @ImportHttpServices(group = "psp", types = PspClient.class)
    static class Psp {

        @Bean
        PaymentGateway pspPaymentGateway(PspClient client) {
            return new PspPaymentGateway(client);
        }
    }
}
