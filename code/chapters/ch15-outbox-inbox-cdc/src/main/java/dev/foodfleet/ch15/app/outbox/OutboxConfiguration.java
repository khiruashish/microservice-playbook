package dev.foodfleet.ch15.app.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OutboxConfiguration {

    /** Replaced by a Kafka-backed publisher when one is defined (chapter 25). */
    @Bean
    @ConditionalOnMissingBean(MessagePublisher.class)
    RecordingMessagePublisher recordingMessagePublisher() {
        return new RecordingMessagePublisher();
    }
}
