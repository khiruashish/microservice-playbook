package dev.foodfleet.ch01.layered.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    /** Injecting a Clock keeps time-dependent logic testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
