package dev.foodfleet.lab12;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

@Configuration
class Wiring {

    /** Built from Boot's RestClient.Builder, which carries the observation: client spans and traceparent headers. */
    @Bean
    RestClient menuClient(RestClient.Builder builder, @Value("${foodfleet.menu.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    PlaceOrder placeOrder(ObservationRegistry observations, RestClient menuClient, KafkaTemplate<String, String> kafka) {
        return new PlaceOrder(observations, menuClient, kafka);
    }
}
