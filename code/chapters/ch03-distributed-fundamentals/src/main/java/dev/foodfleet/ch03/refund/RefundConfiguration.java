package dev.foodfleet.ch03.refund;

import dev.foodfleet.ch03.idempotency.IdempotencyKeyExecutor;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RefundConfiguration {

    /** Keys are remembered for 24 hours: longer than any client's retry horizon. */
    @Bean
    IdempotencyKeyExecutor<RefundController.RefundResponse> refundIdempotency() {
        return new IdempotencyKeyExecutor<>(Clock.systemUTC(), Duration.ofHours(24));
    }
}
