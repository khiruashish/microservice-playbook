package dev.foodfleet.lab07;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * For an ordered consumer (ADR-025): retry in place with exponential backoff, so nothing overtakes a failing record,
 * then publish it to <topic>-dlt with the error in its headers and move on. Invalid messages skip the retries.
 * Spring Boot uses this bean for the default listener container factory.
 */
@Configuration
public class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template,
                                     @Value("${foodfleet.kafka.retry.max-elapsed:31s}") Duration maxElapsed) {
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxElapsedTime(maxElapsed.toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addNotRetryableExceptions(InvalidEvent.class);
        return handler;
    }
}
