package dev.foodfleet.ch25.kafka;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * The error handler for ordinary listeners (Spring Boot applies a single CommonErrorHandler bean to
 * its listener container factory). Without this bean, Spring Kafka retries 10 times with no delay
 * and then only logs the record: a temporary outage silently skips messages.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaErrorHandlingConfig {

    @Bean
    DefaultErrorHandler errorHandler(KafkaOperations<?, ?> template,
                                     @Value("${foodfleet.kafka.retry.initial-interval:1s}") Duration initialInterval) {
        // After the retries: publish to <topic>-dlt, same partition, with exception headers.
        DeadLetterPublishingRecoverer toDlt = new DeadLetterPublishingRecoverer(template);

        // Blocking retries on the partition: 1, 2, 4, 8, 16 s (31 s in total), keeping order.
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(initialInterval.toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(16 * initialInterval.toMillis());

        DefaultErrorHandler handler = new DefaultErrorHandler(toDlt, backOff);
        // Bad data never gets better: straight to the DLT (deserialization failures already are).
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
